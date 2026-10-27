package me.darkz70.voidsmp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Properties;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Еженедельный сброс хранилищ по расписанию из config.yml
 * (по умолчанию — пятница 00:00 Europe/Warsaw).
 * <p>
 * Следующее время сброса хранится на диске, поэтому рестарт сервера
 * не сбивает расписание; если сервер был выключен в момент сброса —
 * сброс выполняется при следующем старте.
 */
public final class ResetScheduler {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final JavaPlugin plugin;
    private final VaultManager vaults;
    private final Path stateFile;

    private LootConfig config;
    private ZoneId zone;
    private long lastResetEpoch = 0;
    private long nextResetEpoch = 0;
    private BukkitTask task;

    public ResetScheduler(JavaPlugin plugin, VaultManager vaults, LootConfig config) {
        this.plugin = plugin;
        this.vaults = vaults;
        this.config = config;
        this.zone = zoneOf(config.resetTimezone, plugin);
        this.stateFile = plugin.getDataFolder().toPath().resolve("reset-state.properties");
    }

    public void onEnable() {
        loadState();
        long now = System.currentTimeMillis();
        if (nextResetEpoch <= 0) {
            // Первый запуск — просто планируем
            nextResetEpoch = computeNext(now);
            saveState();
        } else if (nextResetEpoch <= now) {
            // Сервер был выключен в момент планового сброса — выполняем сейчас
            plugin.getLogger().info("Пропущено плановое время сброса — выполняю сброс при старте");
            vaults.performReset();
            lastResetEpoch = now;
            nextResetEpoch = computeNext(now);
            saveState();
        }
        plugin.getLogger().info("Плановый сброс: " + scheduleDescription() + ", следующий — " + formatNext());
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L * 10, 20L * 30);
    }

    public void onDisable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        saveState();
    }

    private void tick() {
        vaults.tickSave();
        long now = System.currentTimeMillis();
        if (nextResetEpoch > 0 && now >= nextResetEpoch) {
            doReset("плановый");
        }
    }

    /** Выполнить сброс сейчас (плановый или вручную через /voidsmp reset). */
    public int doReset(String reason) {
        int cleared = vaults.performReset();
        long now = System.currentTimeMillis();
        lastResetEpoch = now;
        nextResetEpoch = computeNext(now);
        saveState();
        plugin.getLogger().info("Сброс (" + reason + ") выполнен, с ограничений снято: "
            + cleared + ". Следующий сброс: " + formatNext());
        return cleared;
    }

    /** Обновить конфигурацию после /voidsmp reload и пересчитать время сброса. */
    public void setConfig(LootConfig config) {
        this.config = config;
        this.zone = zoneOf(config.resetTimezone, plugin);
        this.nextResetEpoch = computeNext(System.currentTimeMillis());
        saveState();
    }

    /** Ближайшее расписание: «пт 00:00 (Europe/Warsaw)». */
    public String scheduleDescription() {
        String[] days = {"", "пн", "вт", "ср", "чт", "пт", "сб", "вс"};
        String day = config.resetDayOfWeek >= 1 && config.resetDayOfWeek <= 7
            ? days[config.resetDayOfWeek] : String.valueOf(config.resetDayOfWeek);
        return String.format(Locale.ROOT, "%s %02d:%02d (%s)", day, config.resetHour, config.resetMinute, zone);
    }

    /** Дата и время следующего сброса. */
    public String formatNext() {
        if (nextResetEpoch <= 0) {
            return "—";
        }
        ZonedDateTime time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextResetEpoch), zone);
        return DATE_FORMAT.format(time) + " " + zone;
    }

    public long getNextResetEpoch() {
        return nextResetEpoch;
    }

    public long getLastResetEpoch() {
        return lastResetEpoch;
    }

    // ------------------------------------------------------------------
    // расчёт ближайшего расписания
    // ------------------------------------------------------------------

    private long computeNext(long fromMillis) {
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone);
        DayOfWeek target = DayOfWeek.of(config.resetDayOfWeek);
        ZonedDateTime candidate = now
            .withHour(config.resetHour)
            .withMinute(config.resetMinute)
            .withSecond(0)
            .withNano(0);
        if (candidate.getDayOfWeek() != target) {
            int diff = (target.getValue() - candidate.getDayOfWeek().getValue() + 7) % 7;
            candidate = candidate.plusDays(diff);
        }
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate.toInstant().toEpochMilli();
    }

    private static ZoneId zoneOf(String id, JavaPlugin plugin) {
        try {
            return ZoneId.of(id);
        } catch (Exception e) {
            plugin.getLogger().warning("Некорректный timezone \"" + id + "\", использую Europe/Warsaw");
            return ZoneId.of("Europe/Warsaw");
        }
    }

    // ------------------------------------------------------------------
    // состояние на диске
    // ------------------------------------------------------------------

    private void loadState() {
        if (!Files.exists(stateFile)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось прочитать " + stateFile.getFileName() + ": " + e.getMessage());
            return;
        }
        lastResetEpoch = parseLong(props.getProperty("lastResetEpoch"), 0);
        nextResetEpoch = parseLong(props.getProperty("nextResetEpoch"), 0);
    }

    private void saveState() {
        Properties props = new Properties();
        props.setProperty("lastResetEpoch", String.valueOf(lastResetEpoch));
        props.setProperty("nextResetEpoch", String.valueOf(nextResetEpoch));
        try {
            Files.createDirectories(stateFile.getParent());
            try (OutputStream out = Files.newOutputStream(stateFile)) {
                props.store(out, "VoidSMP reset state");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Не удалось записать " + stateFile.getFileName() + ": " + e.getMessage());
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

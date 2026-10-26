package ru.voidsmp.spawnrates;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import ru.voidsmp.spawnrates.command.SpawnRateCommand;
import ru.voidsmp.spawnrates.config.PluginSettings;
import ru.voidsmp.spawnrates.listener.PreviewService;
import ru.voidsmp.spawnrates.listener.SpawnListener;
import ru.voidsmp.spawnrates.listener.WandListener;
import ru.voidsmp.spawnrates.listener.ZoneCapTask;
import ru.voidsmp.spawnrates.spawn.SpawnRuleEngine;
import ru.voidsmp.spawnrates.storage.PersistenceService;
import ru.voidsmp.spawnrates.storage.StorageException;
import ru.voidsmp.spawnrates.storage.ZoneRecord;
import ru.voidsmp.spawnrates.util.Messages;
import ru.voidsmp.spawnrates.zone.SelectionManager;
import ru.voidsmp.spawnrates.zone.Zone;
import ru.voidsmp.spawnrates.zone.ZoneRegistry;

/**
 * VoidSpawnRates — зоны скорости спавна мобов.
 *
 * <p>Главное, ради чего всё это: зона привязывается к координатам и сразу
 * уходит в SQLite, поэтому она на месте и после рестарта сервера, и после
 * /reload, и после выгрузки чанков. В памяти держится только индекс зон по
 * чанкам — его перестраиваем при старте из базы.</p>
 *
 * <pre>
 *   /spawnrate wand        → ЛКМ/ПКМ по блокам = границы
 *   /spawnrate create farm   → зона сохранена навсегда
 *   /spawnrate set farm multiplier 0.2
 * </pre>
 */
public final class VoidSpawnRatesPlugin extends JavaPlugin {

    public static final String PERMISSION_ADMIN = "voidspawnrates.admin";
    public static final String PERMISSION_VIEW = "voidspawnrates.view";
    public static final String PERMISSION_TELEPORT = "voidspawnrates.teleport";

    private final PluginSettings settings = new PluginSettings();
    private final ZoneRegistry registry = new ZoneRegistry();
    private final SelectionManager selections = new SelectionManager();

    private Messages messages;
    private PersistenceService persistence;
    private SpawnRuleEngine engine;
    private PreviewService preview;
    private WandListener wand;
    private ZoneCapTask capHandler;

    private BukkitTask capTask;
    private BukkitTask previewTask;
    private BukkitTask checkpointTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = Messages.load(this);
        reportSettings(settings.apply(getConfig()));

        persistence = new PersistenceService(this, settings);
        engine = new SpawnRuleEngine(settings.engineOptions());
        preview = new PreviewService(this);
        wand = new WandListener(this);
        capHandler = new ZoneCapTask(this);

        try {
            applyZones(persistence.start());
        } catch (StorageException ex) {
            getLogger().severe("Хранилище зон недоступно: " + ex.getMessage());
            getLogger().severe("Без хранилища зоны не переживут рестарт — отключаю плагин, "
                    + "чтобы не создавать иллюзию работы. Проверьте plugins/VoidSpawnRates/ "
                    + "и storage.type в config.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new SpawnListener(this), this);
        getServer().getPluginManager().registerEvents(wand, this);
        getServer().getPluginManager().registerEvents(capHandler, this);
        getServer().getPluginManager().registerEvents(new InternalListener(), this);

        SpawnRateCommand command = new SpawnRateCommand(this);
        PluginCommand pluginCommand = getCommand("spawnrate");
        if (pluginCommand == null) {
            getLogger().severe("Команда spawnrate не найдена в plugin.yml — /spawnrate работать не будет.");
        } else {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        startTasks();
        getLogger().info("Готово: зон " + registry.size() + ", хранилище " + persistence.describe()
                + (settings.enabled ? "" : " (обработка спавна выключена: enabled: false)"));
    }

    @Override
    public void onDisable() {
        stopTasks();
        if (persistence != null) {
            persistence.shutdown();
        }
        registry.clear();
        selections.dropAll();
        if (preview != null) {
            preview.clearAll();
        }
    }

    /** Заново читает конфиг и зоны; используется /spawnrate reload. */
    public int reloadAll() {
        persistence.flush();
        reloadConfig();
        reportSettings(settings.apply(getConfig()));
        messages = Messages.load(this);
        engine = new SpawnRuleEngine(settings.engineOptions());
        preview.clearAll();
        int count = applyZones(persistence.load());
        startTasks();
        return count;
    }

    private int applyZones(List<ZoneRecord> records) {
        registry.clear();
        for (ZoneRecord record : records) {
            registry.add(new Zone(record));
        }
        registry.rebuildIndex();
        warnAboutMissingWorlds();
        return registry.size();
    }

    /** Зоны в несуществующем мире — частая причина «зона пропала»: мир-то не загружен. */
    private void warnAboutMissingWorlds() {
        for (Zone zone : registry.all()) {
            String world = zone.record().world();
            if (Bukkit.getWorld(world) == null) {
                getLogger().warning("Зона '" + zone.name() + "' указывает на мир '" + world
                        + "', которого сейчас нет — она сработает, как только мир появится.");
            }
        }
    }

    private void startTasks() {
        stopTasks();
        int capInterval = Math.max(settings.capIntervalTicks, 5);
        capTask = Bukkit.getScheduler().runTaskTimer(this, capHandler, capInterval, capInterval);
        int previewInterval = Math.max(settings.previewIntervalTicks, 1);
        previewTask = Bukkit.getScheduler().runTaskTimer(this, preview, previewInterval, previewInterval);
        if (settings.checkpointSeconds > 0) {
            long ticks = settings.checkpointSeconds * 20L;
            checkpointTask = Bukkit.getScheduler().runTaskTimer(this, () -> persistence.checkpoint(), ticks, ticks);
        }
    }

    private void stopTasks() {
        if (capTask != null) {
            capTask.cancel();
            capTask = null;
        }
        if (previewTask != null) {
            previewTask.cancel();
            previewTask = null;
        }
        if (checkpointTask != null) {
            checkpointTask.cancel();
            checkpointTask = null;
        }
    }

    private void reportSettings(List<String> warnings) {
        for (String warning : warnings) {
            getLogger().warning(warning);
        }
    }

    public PluginSettings settings() {
        return settings;
    }

    public ZoneRegistry registry() {
        return registry;
    }

    public SelectionManager selections() {
        return selections;
    }

    public Messages messages() {
        return messages;
    }

    public PersistenceService persistence() {
        return persistence;
    }

    public SpawnRuleEngine engine() {
        return engine;
    }

    public PreviewService preview() {
        return preview;
    }

    public WandListener wand() {
        return wand;
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    /** Убирает выделение и показ рамок за вышедшим игроком. */
    private final class InternalListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            selections.clear(player.getUniqueId());
            preview.forget(player.getUniqueId());
        }
    }
}

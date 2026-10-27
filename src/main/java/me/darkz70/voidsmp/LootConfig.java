package me.darkz70.voidsmp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Конфигурация VoidSMP (config.yml).
 * <p>
 * Парсится через snakeyaml напрямую, без Bukkit API — bootstrap-класс
 * вызывает этот код до создания сервера.
 */
public final class LootConfig {

    /** Одна запись лута: либо material (+count/potion), либо group (список материалов). */
    public record Entry(String material, String potion, int count, int weight, List<String> group) {
        public boolean isGroup() {
            return group != null && !group.isEmpty();
        }
    }

    public final int rolls;
    public final List<Entry> entries;
    /** 1 = Понедельник … 7 = Воскресенье. */
    public final int resetDayOfWeek;
    public final int resetHour;
    public final int resetMinute;
    public final String resetTimezone;

    private LootConfig(int rolls, List<Entry> entries, int resetDayOfWeek, int resetHour,
                       int resetMinute, String resetTimezone) {
        this.rolls = rolls;
        this.entries = entries;
        this.resetDayOfWeek = resetDayOfWeek;
        this.resetHour = resetHour;
        this.resetMinute = resetMinute;
        this.resetTimezone = resetTimezone;
    }

    /** Положить встроенный config.yml в папку плагина, если его ещё нет. */
    public static void ensureDefaultConfig(Path dataDir) throws IOException {
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve("config.yml");
        if (Files.exists(file)) {
            return;
        }
        try (InputStream in = LootConfig.class.getResourceAsStream("/config.yml")) {
            if (in == null) {
                throw new IOException("Встроенный config.yml не найден в jar");
            }
            Files.copy(in, file);
        }
    }

    /** Загрузить config.yml из папки плагина; при его отсутствии — встроенный. */
    public static LootConfig load(Path dataDir) throws IOException {
        Path file = dataDir.resolve("config.yml");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                return parse(in);
            }
        }
        return loadDefault();
    }

    /** Встроенный (из jar) config.yml. */
    public static LootConfig loadDefault() throws IOException {
        try (InputStream in = LootConfig.class.getResourceAsStream("/config.yml")) {
            if (in == null) {
                throw new IOException("Встроенный config.yml не найден в jar");
            }
            return parse(in);
        }
    }

    private static LootConfig parse(InputStream in) throws IOException {
        Object raw;
        try {
            raw = new Yaml().load(in);
        } catch (RuntimeException e) {
            throw new IOException("не удалось разобрать YAML: " + e.getMessage(), e);
        }
        if (!(raw instanceof Map<?, ?> root)) {
            throw new IOException("config.yml пуст или не является картой");
        }

        int rolls = 2;
        List<Entry> entries = new ArrayList<>();
        int day = 5;
        int hour = 0;
        int minute = 0;
        String timezone = "Europe/Warsaw";

        if (root.get("loot") instanceof Map<?, ?> loot) {
            if (loot.get("rolls") instanceof Number n && n.intValue() >= 1) {
                rolls = n.intValue();
            }
            if (loot.get("entries") instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> m)) {
                        continue;
                    }
                    Integer weight = intOf(m.get("weight"));
                    if (weight == null || weight <= 0) {
                        continue;
                    }
                    if (m.get("group") instanceof List<?> group) {
                        List<String> materials = new ArrayList<>();
                        for (Object g : group) {
                            if (g != null && !String.valueOf(g).isBlank()) {
                                materials.add(String.valueOf(g).trim());
                            }
                        }
                        if (!materials.isEmpty()) {
                            entries.add(new Entry(null, null, 1, weight, List.copyOf(materials)));
                        }
                    } else if (m.get("material") instanceof String material && !material.isBlank()) {
                        Integer countRaw = intOf(m.get("count"));
                        int count = countRaw == null ? 1 : Math.max(1, countRaw);
                        String potion = null;
                        if (m.get("potion") instanceof String p && !p.isBlank()) {
                            potion = p.trim();
                        }
                        entries.add(new Entry(material.trim(), potion, count, weight, null));
                    }
                }
            }
        }

        if (root.get("reset") instanceof Map<?, ?> reset) {
            Integer d = intOf(reset.get("day-of-week"));
            if (d != null && d >= 1 && d <= 7) {
                day = d;
            }
            Integer h = intOf(reset.get("hour"));
            if (h != null && h >= 0 && h <= 23) {
                hour = h;
            }
            Integer m = intOf(reset.get("minute"));
            if (m != null && m >= 0 && m <= 59) {
                minute = m;
            }
            if (reset.get("timezone") instanceof String tz && !tz.isBlank()) {
                timezone = tz.trim();
            }
        }

        if (entries.isEmpty()) {
            throw new IOException("в loot.entries нет ни одной корректной записи");
        }
        return new LootConfig(rolls, List.copyOf(entries), day, hour, minute, timezone);
    }

    private static Integer intOf(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}

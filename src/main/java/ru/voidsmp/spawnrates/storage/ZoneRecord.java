package ru.voidsmp.spawnrates.storage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Неизменяемый снимок зоны — ровно то, что уезжает в хранилище.
 *
 * <p>Только примитивы, никаких классов Bukkit: запись можно спокойно отдать
 * async-потоку сохранения и проверить в юнит-тесте без запущенного сервера.</p>
 */
public record ZoneRecord(
        String name,
        String world,
        String worldUuid,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        double multiplier,
        int cap,
        int priority,
        boolean enabled,
        boolean affectSpawners,
        boolean removeExcess,
        Set<String> categories,
        Map<String, Double> mobRules,
        long createdAt,
        long updatedAt) {

    /** cap == CAP_OFF означает «лимита нет». */
    public static final int CAP_OFF = -1;

    public static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{2,32}$");

    public static boolean isValidName(String name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    /**
     * Собирает запись, нормализуя всё, что может прислать игрок или YAML:
     * имя — в нижний регистр, координаты — min/max по парам, множители — в диапазон,
     * коллекции — в неизменяемые копии.
     */
    public static ZoneRecord normalize(
            String name, String world, String worldUuid,
            int x1, int y1, int z1, int x2, int y2, int z2,
            double multiplier, int cap, int priority,
            boolean enabled, boolean affectSpawners, boolean removeExcess,
            Set<String> categories, Map<String, Double> mobRules,
            double maxMultiplier, long nowSeconds) {

        double clamped = clamp(multiplier, 0.0D, maxMultiplier);
        Map<String, Double> rules = new LinkedHashMap<>();
        if (mobRules != null) {
            for (Map.Entry<String, Double> entry : mobRules.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                rules.put(entry.getKey().toUpperCase(Locale.ROOT),
                        clamp(entry.getValue(), 0.0D, maxMultiplier));
            }
        }
        Set<String> cats = new LinkedHashSet<>();
        if (categories != null) {
            for (String cat : categories) {
                if (cat != null && !cat.isBlank()) {
                    cats.add(cat.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return new ZoneRecord(
                name.toLowerCase(Locale.ROOT),
                world,
                worldUuid == null || worldUuid.isBlank() ? null : worldUuid,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2),
                clamped,
                Math.max(cap, CAP_OFF),
                priority,
                enabled,
                affectSpawners,
                removeExcess,
                Collections.unmodifiableSet(cats),
                Collections.unmodifiableMap(rules),
                nowSeconds,
                nowSeconds);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.min(Math.max(value, min), max);
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    /** Число чанков, которые занимает зона (по ним строится индекс). */
    public long chunkCount() {
        long cx = (maxX >> 4) - (minX >> 4) + 1L;
        long cz = (maxZ >> 4) - (minZ >> 4) + 1L;
        return cx * cz;
    }

    public boolean hasCap() {
        return cap >= 0;
    }

    /** Принадлежит ли точка (в блоках) этому параллелепипеду — без учёта мира. */
    public boolean containsBlock(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean isAllCategories() {
        return categories.isEmpty();
    }

    public boolean matchesCategory(String categoryName) {
        return categories.isEmpty() || (categoryName != null && categories.contains(categoryName));
    }

    public double multiplierFor(String entityTypeName) {
        Double rule = mobRules.get(entityTypeName);
        return rule != null ? rule : multiplier;
    }

    /** Меняет один множитель, не трогая остальное. */
    public ZoneRecord withMultiplier(double value, double maxMultiplier, long now) {
        return toBuilder()
                .multiplier(clamp(value, 0.0D, maxMultiplier))
                .updatedAt(now)
                .build();
    }

    public ZoneRecord withCap(int value, long now) {
        return toBuilder().cap(Math.max(value, CAP_OFF)).updatedAt(now).build();
    }

    public ZoneRecord withPriority(int value, long now) {
        return toBuilder().priority(value).updatedAt(now).build();
    }

    public ZoneRecord withCategories(Set<String> value, long now) {
        Set<String> cats = new LinkedHashSet<>();
        for (String cat : value) {
            if (cat != null && !cat.isBlank()) {
                cats.add(cat.trim().toUpperCase(Locale.ROOT));
            }
        }
        return toBuilder().categories(Collections.unmodifiableSet(cats)).updatedAt(now).build();
    }

    public ZoneRecord withFlag(String flag, boolean value, long now) {
        Builder builder = toBuilder().updatedAt(now);
        switch (flag) {
            case "enabled" -> builder.enabled(value);
            case "spawners" -> builder.affectSpawners(value);
            case "despawn" -> builder.removeExcess(value);
            default -> {
                return this;
            }
        }
        return builder.build();
    }

    public ZoneRecord withMobRule(String entityTypeName, double value, double maxMultiplier, long now) {
        Map<String, Double> rules = new LinkedHashMap<>(mobRules);
        rules.put(entityTypeName.toUpperCase(Locale.ROOT), clamp(value, 0.0D, maxMultiplier));
        return toBuilder()
                .mobRules(Collections.unmodifiableMap(rules))
                .updatedAt(now)
                .build();
    }

    public ZoneRecord withoutMobRule(String entityTypeName, long now) {
        Map<String, Double> rules = new LinkedHashMap<>(mobRules);
        rules.remove(entityTypeName.toUpperCase(Locale.ROOT));
        return toBuilder()
                .mobRules(Collections.unmodifiableMap(rules))
                .updatedAt(now)
                .build();
    }

    public ZoneRecord withBox(String newWorld, String newWorldUuid,
                              int x1, int y1, int z1, int x2, int y2, int z2, long now) {
        return toBuilder()
                .world(newWorld)
                .worldUuid(newWorldUuid)
                .minX(Math.min(x1, x2)).minY(Math.min(y1, y2)).minZ(Math.min(z1, z2))
                .maxX(Math.max(x1, x2)).maxY(Math.max(y1, y2)).maxZ(Math.max(z1, z2))
                .updatedAt(now)
                .build();
    }

    /**
     * Ручной builder: записи в Java их не генерируют, а с 19 полями конструктор
     * читать невозможно.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    public static final class Builder {
        private String name;
        private String world;
        private String worldUuid;
        private int minX, minY, minZ, maxX, maxY, maxZ;
        private double multiplier;
        private int cap;
        private int priority;
        private boolean enabled;
        private boolean affectSpawners;
        private boolean removeExcess;
        private Set<String> categories;
        private Map<String, Double> mobRules;
        private long createdAt;
        private long updatedAt;

        private Builder(ZoneRecord base) {
            this.name = base.name;
            this.world = base.world;
            this.worldUuid = base.worldUuid;
            this.minX = base.minX;
            this.minY = base.minY;
            this.minZ = base.minZ;
            this.maxX = base.maxX;
            this.maxY = base.maxY;
            this.maxZ = base.maxZ;
            this.multiplier = base.multiplier;
            this.cap = base.cap;
            this.priority = base.priority;
            this.enabled = base.enabled;
            this.affectSpawners = base.affectSpawners;
            this.removeExcess = base.removeExcess;
            this.categories = base.categories;
            this.mobRules = base.mobRules;
            this.createdAt = base.createdAt;
            this.updatedAt = base.updatedAt;
        }

        public Builder name(String value) { this.name = value; return this; }
        public Builder world(String value) { this.world = value; return this; }
        public Builder worldUuid(String value) { this.worldUuid = value; return this; }
        public Builder minX(int value) { this.minX = value; return this; }
        public Builder minY(int value) { this.minY = value; return this; }
        public Builder minZ(int value) { this.minZ = value; return this; }
        public Builder maxX(int value) { this.maxX = value; return this; }
        public Builder maxY(int value) { this.maxY = value; return this; }
        public Builder maxZ(int value) { this.maxZ = value; return this; }
        public Builder multiplier(double value) { this.multiplier = value; return this; }
        public Builder cap(int value) { this.cap = value; return this; }
        public Builder priority(int value) { this.priority = value; return this; }
        public Builder enabled(boolean value) { this.enabled = value; return this; }
        public Builder affectSpawners(boolean value) { this.affectSpawners = value; return this; }
        public Builder removeExcess(boolean value) { this.removeExcess = value; return this; }
        public Builder categories(Set<String> value) { this.categories = value; return this; }
        public Builder mobRules(Map<String, Double> value) { this.mobRules = value; return this; }
        public Builder createdAt(long value) { this.createdAt = value; return this; }
        public Builder updatedAt(long value) { this.updatedAt = value; return this; }

        public ZoneRecord build() {
            return new ZoneRecord(name, world, worldUuid, minX, minY, minZ, maxX, maxY, maxZ,
                    multiplier, cap, priority, enabled, affectSpawners, removeExcess,
                    categories, mobRules, createdAt, updatedAt);
        }
    }
}

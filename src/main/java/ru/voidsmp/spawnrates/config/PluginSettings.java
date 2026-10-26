package ru.voidsmp.spawnrates.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;

import ru.voidsmp.spawnrates.spawn.OverlapPolicy;
import ru.voidsmp.spawnrates.spawn.SpawnRuleEngine;

/**
 * Настройки плагина одним объектом. Читается на старте и по /spawnrate reload,
 * поля volatile — чтобы async-поток сохранения видел актуальные значения без блокировок.
 */
public final class PluginSettings {

    public enum StorageMode {
        AUTO, SQLITE, YAML
    }

    private static final Set<String> DEFAULT_REASONS =
            Set.of("NATURAL", "JOCKEY", "PATROL", "RAID", "VILLAGE_INVASION");
    private static final Set<String> DEFAULT_SPAWNER_REASONS =
            Set.of("SPAWNER", "TRIAL_SPAWNER", "OMINOUS_ITEM_SPAWNER");

    public volatile boolean enabled = true;

    public volatile double maxMultiplier = 10.0D;
    public volatile int maxZoneSize = 256;
    public volatile int maxZones = 200;
    public volatile int maxRemovalPerTick = 32;

    public volatile OverlapPolicy overlap = OverlapPolicy.PRIORITY;
    public volatile Set<String> reasons = DEFAULT_REASONS;
    public volatile Set<String> spawnerReasons = DEFAULT_SPAWNER_REASONS;
    public volatile boolean extraSpawns = true;
    public volatile int maxExtraSpawns = 3;
    public volatile boolean denyAtCap = true;
    public volatile boolean abortOnHardDeny = true;

    public volatile int capIntervalTicks = 40;
    public volatile boolean skipWhenNoPlayersNearby = true;
    public volatile int playerDistance = 128;
    public volatile boolean removeExcessDefault = false;
    public volatile boolean protectNamedMobs = true;
    public volatile boolean protectRiders = true;

    public volatile StorageMode storageMode = StorageMode.AUTO;
    public volatile String databaseFile = "data.db";
    public volatile String yamlFile = "zones.yml";
    public volatile int checkpointSeconds = 300;

    public volatile Material wandMaterial = Material.WOODEN_AXE;

    public volatile Particle particle = Particle.END_ROD;
    public volatile int pointsPerEdge = 16;
    public volatile int previewDistance = 96;
    public volatile int previewIntervalTicks = 20;
    public volatile boolean hud = true;

    public volatile boolean debug = false;

    /**
     * Применяет конфиг. Всё, что нельзя распарсить, молча возвращается к
     * безопасному значению, но попадает в список предупреждений — их печатает
     * основной класс, чтобы не молоть мусор в логе на каждый reload.
     */
    public List<String> apply(FileConfiguration config) {
        List<String> warnings = new ArrayList<>();

        enabled = config.getBoolean("enabled", enabled);

        maxMultiplier = clampDouble(config.getDouble("limits.max-multiplier", maxMultiplier), 0.1D, 1000.0D);
        maxZoneSize = (int) clampDouble(config.getInt("limits.max-zone-size", maxZoneSize), 16, 4096);
        maxZones = (int) clampDouble(config.getInt("limits.max-zones", maxZones), 1, 100000);
        maxRemovalPerTick = (int) clampDouble(config.getInt("limits.max-removal-per-tick", maxRemovalPerTick), 0, 1024);

        overlap = OverlapPolicy.parse(config.getString("spawn.overlap", "PRIORITY"), OverlapPolicy.PRIORITY);
        reasons = parseReasons(config.getStringList("spawn.reasons"), DEFAULT_REASONS, warnings, "spawn.reasons");
        spawnerReasons = parseReasons(config.getStringList("spawn.spawner-reasons"),
                DEFAULT_SPAWNER_REASONS, warnings, "spawn.spawner-reasons");
        extraSpawns = config.getBoolean("spawn.extra-spawns", extraSpawns);
        maxExtraSpawns = (int) clampDouble(config.getInt("spawn.max-extra-spawns", maxExtraSpawns), 0, 16);
        denyAtCap = config.getBoolean("spawn.deny-at-cap", denyAtCap);
        abortOnHardDeny = config.getBoolean("spawn.abort-spawn-attempts", abortOnHardDeny);

        capIntervalTicks = (int) clampDouble(config.getInt("cap.check-interval-ticks", capIntervalTicks), 5, 20 * 600);
        skipWhenNoPlayersNearby = config.getBoolean("cap.skip-when-no-players-nearby", skipWhenNoPlayersNearby);
        playerDistance = (int) clampDouble(config.getInt("cap.player-distance", playerDistance), 0, 8192);
        removeExcessDefault = config.getBoolean("cap.remove-excess-default", removeExcessDefault);
        protectNamedMobs = config.getBoolean("cap.protect-named-mobs", protectNamedMobs);
        protectRiders = config.getBoolean("cap.protect-riders", protectRiders);

        StorageMode parsedMode = StorageMode.AUTO;
        String modeRaw = config.getString("storage.type", "AUTO");
        if (modeRaw != null && !modeRaw.isBlank()) {
            try {
                parsedMode = StorageMode.valueOf(modeRaw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                warnings.add("storage.type: неизвестный режим '" + modeRaw + "', использую AUTO");
            }
        }
        storageMode = parsedMode;
        databaseFile = safeFileName(config.getString("storage.file", databaseFile), "data.db", warnings);
        yamlFile = safeFileName(config.getString("storage.yaml-file", yamlFile), "zones.yml", warnings);
        checkpointSeconds = (int) clampDouble(config.getInt("storage.checkpoint-seconds", checkpointSeconds), 0, 86400);

        String particleName = config.getString("preview.particle", "END_ROD");
        particle = parseParticle(particleName, warnings);
        Material parsedWand = Material.matchMaterial(
                config.getString("selection.wand", "WOODEN_AXE") == null
                        ? "WOODEN_AXE" : config.getString("selection.wand", "WOODEN_AXE").trim());
        if (parsedWand == null) {
            warnings.add("selection.wand: неизвестный материал, использую WOODEN_AXE");
            parsedWand = Material.WOODEN_AXE;
        }
        wandMaterial = parsedWand;
        pointsPerEdge = (int) clampDouble(config.getInt("preview.points-per-edge", pointsPerEdge), 2, 64);
        previewDistance = (int) clampDouble(config.getInt("preview.distance", previewDistance), 8, 512);
        previewIntervalTicks = (int) clampDouble(config.getInt("preview.interval-ticks", previewIntervalTicks), 1, 200);
        hud = config.getBoolean("preview.hud", hud);

        debug = config.getBoolean("debug", debug);
        return warnings;
    }

    public SpawnRuleEngine.Options engineOptions() {
        return new SpawnRuleEngine.Options()
                .overlap(overlap)
                .denyAtCap(denyAtCap)
                .extraSpawns(extraSpawns)
                .maxExtraSpawns(maxExtraSpawns)
                .abortOnHardDeny(abortOnHardDeny);
    }

    public boolean isNaturalReason(String reasonName) {
        return reasons.contains(reasonName);
    }

    public boolean isSpawnerReason(String reasonName) {
        return spawnerReasons.contains(reasonName);
    }

    /** Учитывается ли вообще причина спавна (нужно для быстрого выхода из слушателя). */
    public boolean isRelevantReason(String reasonName) {
        return reasons.contains(reasonName) || spawnerReasons.contains(reasonName);
    }

    public double clampMultiplier(double value) {
        return clampDouble(value, 0.0D, maxMultiplier);
    }

    private static Set<String> parseReasons(List<String> raw, Set<String> fallback,
                                            List<String> warnings, String path) {
        if (raw == null || raw.isEmpty()) {
            return fallback;
        }
        Set<String> out = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String name = value.trim().toUpperCase(Locale.ROOT);
            if (!isKnownReason(name)) {
                warnings.add(path + ": неизвестная причина спавна '" + name + "' — пропущено");
                continue;
            }
            out.add(name);
        }
        return out.isEmpty() ? fallback : Set.copyOf(out);
    }

    private static boolean isKnownReason(String name) {
        for (org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason
                : org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.values()) {
            if (reason.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static Particle parseParticle(String name, List<String> warnings) {
        if (name == null || name.isBlank()) {
            return Particle.END_ROD;
        }
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warnings.add("preview.particle: неизвестные частицы '" + name + "', использую END_ROD");
            return Particle.END_ROD;
        }
    }

    private static String safeFileName(String raw, String fallback, List<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String name = raw.trim().replace('\\', '/');
        if (name.contains("/") || name.contains("..") || name.contains(":")) {
            warnings.add("Имя файла '" + raw + "' недопустимо, использую " + fallback);
            return fallback;
        }
        return name;
    }

    private static double clampDouble(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.min(Math.max(value, min), max);
    }
}

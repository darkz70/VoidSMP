package me.darkz70.quirks.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Сканирование блоков вокруг игрока + простые кулдауны. */
public final class ScanUtil {

    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    private ScanUtil() {}

    /** Считает блоки кубом (2r+1)^3 вокруг центра. Незагруженные чанки пропускает. */
    public static int countBlocks(Location center, int radius, Predicate<Material> filter) {
        World world = center.getWorld();
        if (world == null) return 0;
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                // не подгружаем чанки ради скана — иначе будут лаги на большом радиусе
                if (!world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) continue;
                for (int dy = -radius; dy <= radius; dy++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (filter.test(block.getType())) count++;
                }
            }
        }
        return count;
    }

    /** true, если кулдаун истёк (и обновляет метку). */
    public static boolean tryUse(UUID player, String ability, long cooldownMs) {
        long now = System.currentTimeMillis();
        String key = player + ":" + ability;
        Long last = COOLDOWNS.get(key);
        if (last != null && now - last < cooldownMs) return false;
        COOLDOWNS.put(key, now);
        return true;
    }

    /** Сколько миллисекунд осталось до конца кулдауна (0, если готов). */
    public static long remaining(UUID player, String ability, long cooldownMs) {
        Long last = COOLDOWNS.get(player + ":" + ability);
        if (last == null) return 0;
        return Math.max(0, cooldownMs - (System.currentTimeMillis() - last));
    }

    public static void stamp(UUID player, String ability) {
        COOLDOWNS.put(player + ":" + ability, System.currentTimeMillis());
    }
}

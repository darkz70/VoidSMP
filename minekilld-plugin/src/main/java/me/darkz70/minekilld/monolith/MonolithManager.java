package me.darkz70.minekilld.monolith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import me.darkz70.minekilld.MinekilldPlugin;
import me.darkz70.minekilld.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class MonolithManager {

    private static final Set<Material> PROTECTED_BLOCKS = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.ENDER_CHEST,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.HOPPER,
            Material.DROPPER, Material.DISPENSER, Material.CRAFTING_TABLE,
            Material.ENCHANTING_TABLE, Material.BREWING_STAND, Material.BEACON,
            Material.SPAWNER, Material.DRAGON_EGG, Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
            Material.RESPAWN_ANCHOR, Material.NETHER_PORTAL, Material.END_PORTAL,
            Material.END_PORTAL_FRAME, Material.BEDROCK, Material.ANVIL,
            Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL, Material.LECTERN,
            Material.JUKEBOX, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.TORCH, Material.WALL_TORCH, Material.LANTERN, Material.SOUL_LANTERN,
            Material.COBBLESTONE, Material.STONE_BRICKS, Material.BRICKS,
            Material.GLASS, Material.TINTED_GLASS, Material.IRON_BARS,
            Material.RAIL, Material.POWERED_RAIL, Material.DETECTOR_RAIL, Material.ACTIVATOR_RAIL
    );

    public record MonolithRecord(String world, int x, int y, int z, int height, int axis) {
        public String serialize() {
            return world + ":" + x + "," + y + "," + z + "," + height + "," + axis;
        }

        public static MonolithRecord deserialize(String raw) {
            if (raw == null) return null;
            int colon = raw.lastIndexOf(':');
            if (colon <= 0) return null;
            String w = raw.substring(0, colon);
            String[] parts = raw.substring(colon + 1).split(",");
            if (parts.length != 5) return null;
            try {
                return new MonolithRecord(w,
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()),
                        Integer.parseInt(parts[3].trim()),
                        Integer.parseInt(parts[4].trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private final MinekilldPlugin plugin;
    private final List<MonolithRecord> monoliths = new ArrayList<>();
    private final Set<World.Environment> allowedEnvironments = EnumSet.noneOf(World.Environment.class);

    private BukkitTask spawnTask;
    private BukkitTask ambientTask;

    private boolean enabled;
    private int intervalMinutes;
    private double minDistance;
    private double maxDistance;
    private int minHeight;
    private int maxHeight;
    private double cryingChance;
    private boolean announce;
    private String announceMessage;
    private boolean ambientParticles;
    private double ambientRadius;
    private int ambientIntervalSeconds;

    private long nextSpawnAtMs;

    public MonolithManager(MinekilldPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadData(YamlConfiguration data) {
        monoliths.clear();
        for (String raw : data.getStringList("monoliths")) {
            MonolithRecord rec = MonolithRecord.deserialize(raw);
            if (rec != null) monoliths.add(rec);
        }
    }

    public void saveData(YamlConfiguration data) {
        List<String> list = new ArrayList<>(monoliths.size());
        for (MonolithRecord rec : monoliths) {
            list.add(rec.serialize());
        }
        data.set("monoliths", list);
    }

    public void reload() {
        stopTasks();

        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("monolith.enabled", true);
        intervalMinutes = Math.max(1, cfg.getInt("monolith.interval-minutes", 60));
        minDistance = Math.max(16.0, cfg.getDouble("monolith.min-distance", 100.0));
        maxDistance = Math.max(minDistance + 5.0, cfg.getDouble("monolith.max-distance", 140.0));
        minHeight = Math.max(4, cfg.getInt("monolith.min-height", 10));
        maxHeight = Math.max(minHeight, cfg.getInt("monolith.max-height", 14));
        cryingChance = Math.max(0.0, Math.min(1.0, cfg.getDouble("monolith.crying-obsidian-chance", 0.0)));
        announce = cfg.getBoolean("monolith.announce", false);
        announceMessage = cfg.getString("monolith.announce-message",
                "&5✦ Где-то в мире пробудился обсидиановый монолит...");
        ambientParticles = cfg.getBoolean("monolith.ambient-particles", true);
        ambientRadius = Math.max(8.0, cfg.getDouble("monolith.ambient-particle-radius", 48.0));
        ambientIntervalSeconds = Math.max(1, cfg.getInt("monolith.ambient-particle-interval-seconds", 5));

        allowedEnvironments.clear();
        for (String envRaw : cfg.getStringList("monolith.allowed-environments")) {
            if (envRaw == null || envRaw.isBlank()) continue;
            try {
                allowedEnvironments.add(World.Environment.valueOf(envRaw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Неизвестное измерение в monolith.allowed-environments: " + envRaw);
            }
        }
        if (allowedEnvironments.isEmpty()) {
            allowedEnvironments.add(World.Environment.NORMAL);
        }

        if (!enabled) {
            nextSpawnAtMs = 0L;
            return;
        }

        long intervalTicks = intervalMinutes * 60L * 20L;
        nextSpawnAtMs = System.currentTimeMillis() + intervalMinutes * 60_000L;

        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            nextSpawnAtMs = System.currentTimeMillis() + intervalMinutes * 60_000L;
            spawnNearRandomPlayer(null, null);
        }, intervalTicks, intervalTicks);

        if (ambientParticles) {
            long ambTicks = ambientIntervalSeconds * 20L;
            ambientTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAmbientParticles, ambTicks, ambTicks);
        }
    }

    public void stopTasks() {
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<MonolithRecord> monoliths() {
        return Collections.unmodifiableList(monoliths);
    }

    public long minutesUntilNextSpawn() {
        if (!enabled || nextSpawnAtMs <= 0L) return -1L;
        return Math.max(0L, (nextSpawnAtMs - System.currentTimeMillis()) / 60_000L);
    }

    public long secondsUntilNextSpawn() {
        if (!enabled || nextSpawnAtMs <= 0L) return -1L;
        return Math.max(0L, (nextSpawnAtMs - System.currentTimeMillis()) / 1000L);
    }

    public void spawnNearRandomPlayer(Player explicitTarget, Consumer<Location> callback) {
        Player chosen = explicitTarget;
        if (chosen == null) {
            List<Player> candidates = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (allowedEnvironments.contains(p.getWorld().getEnvironment())) {
                    candidates.add(p);
                }
            }
            if (candidates.isEmpty()) {
                if (callback != null) callback.accept(null);
                return;
            }
            chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        }

        Location anchor = chosen.getLocation().clone();
        World world = anchor.getWorld();
        if (world == null) {
            if (callback != null) callback.accept(null);
            return;
        }

        trySpawnCandidate(world, anchor, 20, callback);
    }

    private void trySpawnCandidate(World world, Location anchor, int attemptsLeft, Consumer<Location> callback) {
        if (!plugin.isEnabled() || attemptsLeft <= 0) {
            if (callback != null) callback.accept(null);
            return;
        }

        var rng = ThreadLocalRandom.current();
        double angle = rng.nextDouble() * Math.PI * 2.0;
        double dist = minDistance + rng.nextDouble() * (maxDistance - minDistance);
        int x = anchor.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
        int z = anchor.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

        WorldBorder border = world.getWorldBorder();
        if (!border.isInside(new Location(world, x + 0.5, 64.0, z + 0.5))
                || isNearExistingMonolith(world.getName(), x, z)) {
            trySpawnCandidate(world, anchor, attemptsLeft - 1, callback);
            return;
        }

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!plugin.isEnabled()) return;
                int height = minHeight + rng.nextInt(maxHeight - minHeight + 1);
                Integer groundY = findSurfaceY(world, x, z, height);
                if (groundY == null || !isAreaSafe(world, x, groundY + 1, z, height)) {
                    trySpawnCandidate(world, anchor, attemptsLeft - 1, callback);
                    return;
                }

                int baseY = groundY + 1;
                int axis = rng.nextBoolean() ? 0 : 1;
                buildMonolith(world, x, baseY, z, height, axis);
                Location placed = new Location(world, x + 0.5, baseY, z + 0.5);
                if (callback != null) callback.accept(placed);
            });
        }).exceptionally(ex -> {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        trySpawnCandidate(world, anchor, attemptsLeft - 1, callback));
            }
            return null;
        });
    }

    private boolean isNearExistingMonolith(String worldName, int x, int z) {
        for (MonolithRecord rec : monoliths) {
            if (!rec.world().equals(worldName)) continue;
            long dx = rec.x() - x;
            long dz = rec.z() - z;
            if (dx * dx + dz * dz < 24L * 24L) {
                return true;
            }
        }
        return false;
    }

    private Integer findSurfaceY(World world, int x, int z, int height) {
        int startY = Math.min(world.getMaxHeight() - 2, world.getHighestBlockYAt(x, z) + 2);
        int minY = world.getMinHeight() + 5;
        for (int y = startY; y >= minY; y--) {
            Block b = world.getBlockAt(x, y, z);
            if (b.isLiquid()) {
                return null;
            }
            Material type = b.getType();
            if (b.isEmpty() || !type.isSolid() || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type)) {
                continue;
            }
            if (y + 1 + height + 2 >= world.getMaxHeight()) {
                return null;
            }
            return y;
        }
        return null;
    }

    private boolean isAreaSafe(World world, int cx, int baseY, int cz, int height) {
        // Не ставим на обрыв, если углы фундамента висят более чем на 5 блоков в воздухе
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean hasSupport = false;
                for (int dy = -1; dy >= -5; dy--) {
                    Block below = world.getBlockAt(cx + dx, baseY + dy, cz + dz);
                    if (below.getType().isSolid() && !below.isLiquid()) {
                        hasSupport = true;
                        break;
                    }
                }
                if (!hasSupport) return false;
            }
        }

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= height; dy++) {
                    Block b = world.getBlockAt(cx + dx, baseY + dy, cz + dz);
                    if (b.isLiquid()) return false;
                    Material m = b.getType();
                    if (isProtectedBlock(m)) return false;
                }
            }
        }
        return true;
    }

    private static boolean isProtectedBlock(Material m) {
        if (PROTECTED_BLOCKS.contains(m)) return true;
        return Tag.BEDS.isTagged(m)
                || Tag.DOORS.isTagged(m)
                || Tag.TRAPDOORS.isTagged(m)
                || Tag.SIGNS.isTagged(m)
                || Tag.PLANKS.isTagged(m)
                || Tag.SLABS.isTagged(m)
                || Tag.STAIRS.isTagged(m)
                || Tag.WALLS.isTagged(m)
                || Tag.FENCES.isTagged(m)
                || Tag.FENCE_GATES.isTagged(m)
                || Tag.WOOL.isTagged(m)
                || Tag.SHULKER_BOXES.isTagged(m);
    }

    private void buildMonolith(World world, int cx, int baseY, int cz, int height, int axis) {
        var rng = ThreadLocalRandom.current();

        // Фундамент на склонах (3x3 до 4 блоков вниз)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy >= -4; dy--) {
                    Block b = world.getBlockAt(cx + dx, baseY + dy, cz + dz);
                    if (b.isEmpty() || b.isLiquid() || !b.getType().isSolid()) {
                        b.setType(Material.OBSIDIAN, false);
                    } else {
                        break;
                    }
                }
            }
        }

        // Расчистка листвы/растительности в зоне монолита, чтобы не тонул в кронах деревьев
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= height; dy++) {
                    Block b = world.getBlockAt(cx + dx, baseY + dy, cz + dz);
                    Material m = b.getType();
                    if (!b.isEmpty() && (!m.isSolid() || Tag.LEAVES.isTagged(m) || Tag.LOGS.isTagged(m))) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }

        // Пьедестал 3x3 (dy = 0)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                placeMonolithBlock(world.getBlockAt(cx + dx, baseY, cz + dz), rng);
            }
        }

        // Переходный крест (dy = 1)
        placeMonolithBlock(world.getBlockAt(cx, baseY + 1, cz), rng);
        placeMonolithBlock(world.getBlockAt(cx - 1, baseY + 1, cz), rng);
        placeMonolithBlock(world.getBlockAt(cx + 1, baseY + 1, cz), rng);
        placeMonolithBlock(world.getBlockAt(cx, baseY + 1, cz - 1), rng);
        placeMonolithBlock(world.getBlockAt(cx, baseY + 1, cz + 1), rng);

        // Тело монолита: высокая плита 3x1 (dy = 2 .. height - 1)
        for (int dy = 2; dy < height; dy++) {
            for (int offset = -1; offset <= 1; offset++) {
                int bx = axis == 0 ? cx + offset : cx;
                int bz = axis == 0 ? cz : cz + offset;
                placeMonolithBlock(world.getBlockAt(bx, baseY + dy, bz), rng);
            }
        }

        Location mid = new Location(world, cx + 0.5, baseY + height * 0.5, cz + 0.5);
        world.spawnParticle(Particle.REVERSE_PORTAL, mid, 60, 1.2, height * 0.35, 1.2, 0.03);
        world.spawnParticle(Particle.PORTAL, mid, 80, 1.4, height * 0.4, 1.4, 0.05);
        world.spawnParticle(Particle.END_ROD, mid, 16, 0.9, height * 0.35, 0.9, 0.02);
        world.playSound(mid, Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 0.6f);
        world.playSound(mid, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.6f, 0.7f);

        monoliths.add(new MonolithRecord(world.getName(), cx, baseY, cz, height, axis));
        plugin.saveData();

        if (announce && announceMessage != null && !announceMessage.isBlank()) {
            Bukkit.broadcast(Msg.color(announceMessage));
        }
    }

    private void placeMonolithBlock(Block block, ThreadLocalRandom rng) {
        Material mat = cryingChance > 0.0 && rng.nextDouble() < cryingChance
                ? Material.CRYING_OBSIDIAN
                : Material.OBSIDIAN;
        block.setType(mat, false);
    }

    private void tickAmbientParticles() {
        if (Bukkit.getOnlinePlayers().isEmpty() || monoliths.isEmpty()) return;

        boolean dirty = false;
        double radSq = ambientRadius * ambientRadius;
        Iterator<MonolithRecord> it = monoliths.iterator();
        while (it.hasNext()) {
            MonolithRecord rec = it.next();
            World world = Bukkit.getWorld(rec.world());
            if (world == null || !world.isChunkLoaded(rec.x() >> 4, rec.z() >> 4)) continue;

            int intact = 0;
            for (int dy = 0; dy < rec.height(); dy++) {
                Material m = world.getBlockAt(rec.x(), rec.y() + dy, rec.z()).getType();
                if (m == Material.OBSIDIAN || m == Material.CRYING_OBSIDIAN) {
                    intact++;
                }
            }
            if (intact < Math.max(2, rec.height() / 2)) {
                it.remove();
                dirty = true;
                continue;
            }

            Location mid = new Location(world, rec.x() + 0.5, rec.y() + rec.height() * 0.5, rec.z() + 0.5);
            boolean playerNear = false;
            for (Player p : world.getPlayers()) {
                if (p.getLocation().distanceSquared(mid) <= radSq) {
                    playerNear = true;
                    break;
                }
            }
            if (!playerNear) continue;

            world.spawnParticle(Particle.REVERSE_PORTAL, mid, 6, 1.1, rec.height() * 0.38, 1.1, 0.01);
            world.spawnParticle(Particle.ASH, mid, 5, 1.1, rec.height() * 0.4, 1.1, 0.005);
        }

        if (dirty) {
            plugin.saveData();
        }
    }
}

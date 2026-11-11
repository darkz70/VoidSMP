package me.darkz70.minekilld.egg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.minekilld.MinekilldPlugin;
import me.darkz70.minekilld.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public final class DragonEggManager {

    private BlockData dragonEggData;

    private BlockData eggData() {
        if (dragonEggData == null) {
            dragonEggData = Material.DRAGON_EGG.createBlockData();
        }
        return dragonEggData;
    }

    public record BlockPos(String world, int x, int y, int z) {
        public int chunkX() {
            return x >> 4;
        }

        public int chunkZ() {
            return z >> 4;
        }

        public String serialize() {
            return world + ":" + x + "," + y + "," + z;
        }

        public static BlockPos deserialize(String raw) {
            if (raw == null) return null;
            int colon = raw.lastIndexOf(':');
            if (colon <= 0) return null;
            String w = raw.substring(0, colon);
            String[] coords = raw.substring(colon + 1).split(",");
            if (coords.length != 3) return null;
            try {
                return new BlockPos(w,
                        Integer.parseInt(coords[0].trim()),
                        Integer.parseInt(coords[1].trim()),
                        Integer.parseInt(coords[2].trim()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }

    private record ChunkPos(String world, int cx, int cz) {}

    private final MinekilldPlugin plugin;
    private final Set<BlockPos> placedEggs = new LinkedHashSet<>();
    private final Map<ChunkPos, Long> scannedChunks = new HashMap<>();

    private final List<PotionEffectType> eligibleEffects = new ArrayList<>();
    private final List<Particle> eligibleParticles = new ArrayList<>();

    private BukkitTask particleTask;
    private BukkitTask effectTask;

    private boolean enabled;
    private double radius;
    private int effectIntervalSeconds;
    private int effectDurationSeconds;
    private int minLevel;
    private int maxLevel;
    private int particleIntervalSeconds;
    private int particleCount;
    private boolean checkFullInventory;
    private boolean heldAffectsNearby;
    private boolean includeDroppedItems;
    private boolean ignoreSpectator;
    private boolean actionbarNotify;

    private long nextEffectAtMs;
    private long nextParticleAtMs;
    private boolean dirty;

    public DragonEggManager(MinekilldPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadData(YamlConfiguration data) {
        placedEggs.clear();
        for (String raw : data.getStringList("eggs")) {
            BlockPos pos = BlockPos.deserialize(raw);
            if (pos != null) placedEggs.add(pos);
        }
    }

    public void saveData(YamlConfiguration data) {
        List<String> list = new ArrayList<>(placedEggs.size());
        for (BlockPos pos : placedEggs) {
            list.add(pos.serialize());
        }
        data.set("eggs", list);
        dirty = false;
    }

    public void reload() {
        stopTasks();

        var cfg = plugin.getConfig();
        enabled = cfg.getBoolean("egg.enabled", true);
        radius = Math.max(1.0, cfg.getDouble("egg.radius", 20.0));
        effectIntervalSeconds = Math.max(1, cfg.getInt("egg.effect-interval-seconds", 600));
        effectDurationSeconds = Math.max(1, cfg.getInt("egg.effect-duration-seconds", 10));
        minLevel = Math.max(1, cfg.getInt("egg.min-level", 1));
        maxLevel = Math.max(minLevel, cfg.getInt("egg.max-level", 10));
        particleIntervalSeconds = Math.max(1, cfg.getInt("egg.particle-interval-seconds", 10));
        particleCount = Math.max(1, cfg.getInt("egg.particle-count", 24));
        checkFullInventory = cfg.getBoolean("egg.check-full-inventory", false);
        heldAffectsNearby = cfg.getBoolean("egg.held-affects-nearby", true);
        includeDroppedItems = cfg.getBoolean("egg.include-dropped-items", true);
        ignoreSpectator = cfg.getBoolean("egg.ignore-spectator", true);
        actionbarNotify = cfg.getBoolean("egg.actionbar-notify", true);

        rebuildEffectsPool(cfg.getStringList("egg.excluded-effects"));
        rebuildParticlesPool(cfg.getStringList("egg.excluded-particles"));

        if (!enabled) {
            nextEffectAtMs = 0L;
            nextParticleAtMs = 0L;
            return;
        }

        long pTicks = particleIntervalSeconds * 20L;
        long eTicks = effectIntervalSeconds * 20L;
        long now = System.currentTimeMillis();
        nextParticleAtMs = now + particleIntervalSeconds * 1000L;
        nextEffectAtMs = now + effectIntervalSeconds * 1000L;

        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            nextParticleAtMs = System.currentTimeMillis() + particleIntervalSeconds * 1000L;
            spawnParticlesPulse();
        }, pTicks, pTicks);

        effectTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            nextEffectAtMs = System.currentTimeMillis() + effectIntervalSeconds * 1000L;
            triggerEffectsPulse();
        }, eTicks, eTicks);
    }

    public void stopTasks() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
    }

    @SuppressWarnings("deprecation")
    private void rebuildEffectsPool(List<String> excludedRaw) {
        eligibleEffects.clear();
        Set<String> excluded = new HashSet<>();
        for (String s : excludedRaw) {
            if (s != null && !s.isBlank()) {
                excluded.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (PotionEffectType type : PotionEffectType.values()) {
            if (type == null) continue;
            String key = type.getKey().getKey().toLowerCase(Locale.ROOT);
            if (!excluded.contains(key)) {
                eligibleEffects.add(type);
            }
        }
    }

    private void rebuildParticlesPool(List<String> excludedRaw) {
        eligibleParticles.clear();
        Set<String> excluded = new HashSet<>();
        for (String s : excludedRaw) {
            if (s != null && !s.isBlank()) {
                excluded.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        for (Particle p : Particle.values()) {
            if (excluded.contains(p.name())) continue;
            Class<?> dt = p.getDataType();
            if (dt == Void.class
                    || dt == Particle.DustOptions.class
                    || dt == Particle.DustTransition.class
                    || dt == Color.class
                    || dt == BlockData.class
                    || dt == ItemStack.class
                    || dt == Float.class
                    || dt == Integer.class) {
                eligibleParticles.add(p);
            }
        }
    }

    public void trackBlock(Block block) {
        if (block == null) return;
        BlockPos pos = new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (placedEggs.add(pos)) {
            dirty = true;
            plugin.saveData();
        }
    }

    public void untrackBlock(Block block) {
        if (block == null) return;
        BlockPos pos = new BlockPos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        if (placedEggs.remove(pos)) {
            scannedChunks.remove(new ChunkPos(pos.world(), pos.chunkX(), pos.chunkZ()));
            dirty = true;
            plugin.saveData();
        }
    }

    public void forceRescanLoadedAroundPlayers() {
        scannedChunks.clear();
        refreshEggsAroundOnlinePlayers();
    }

    public int trackedEggCount() {
        return placedEggs.size();
    }

    public int eligibleEffectCount() {
        return eligibleEffects.size();
    }

    public int eligibleParticleCount() {
        return eligibleParticles.size();
    }

    public long secondsUntilNextEffect() {
        if (!enabled || nextEffectAtMs <= 0L) return -1L;
        return Math.max(0L, (nextEffectAtMs - System.currentTimeMillis()) / 1000L);
    }

    public long secondsUntilNextParticles() {
        if (!enabled || nextParticleAtMs <= 0L) return -1L;
        return Math.max(0L, (nextParticleAtMs - System.currentTimeMillis()) / 1000L);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Обновляет известные поставленные яйца в загруженных чанках вокруг онлайн-игроков.
     * Использует O(1)-проверку палитры секций чанка Chunk#contains(BlockData),
     * поэтому сканирование чанка происходит только если в нём реально есть DRAGON_EGG.
     */
    private void refreshEggsAroundOnlinePlayers() {
        long now = System.currentTimeMillis();

        // 1) Проверяем уже известные яйца, если их чанки загружены
        Iterator<BlockPos> it = placedEggs.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            World world = Bukkit.getWorld(pos.world());
            if (world == null || !world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) continue;
            if (world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() != Material.DRAGON_EGG) {
                it.remove();
                scannedChunks.remove(new ChunkPos(pos.world(), pos.chunkX(), pos.chunkZ()));
                dirty = true;
            }
        }

        // 2) Проверяем загруженные чанки вокруг онлайн-игроков
        int chunkRadius = Math.max(2, (int) Math.ceil(radius / 16.0) + 1);
        Set<ChunkPos> nearbyChunks = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            int pcx = player.getLocation().getBlockX() >> 4;
            int pcz = player.getLocation().getBlockZ() >> 4;
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    int cx = pcx + dx;
                    int cz = pcz + dz;
                    if (world.isChunkLoaded(cx, cz)) {
                        nearbyChunks.add(new ChunkPos(world.getName(), cx, cz));
                    }
                }
            }
        }

        for (ChunkPos cp : nearbyChunks) {
            World world = Bukkit.getWorld(cp.world());
            if (world == null || !world.isChunkLoaded(cp.cx(), cp.cz())) continue;
            Chunk chunk = world.getChunkAt(cp.cx(), cp.cz());
            if (!chunk.contains(eggData())) {
                boolean removedAny = placedEggs.removeIf(p ->
                        p.world().equals(cp.world()) && p.chunkX() == cp.cx() && p.chunkZ() == cp.cz());
                if (removedAny) dirty = true;
                scannedChunks.put(cp, now);
                continue;
            }

            boolean hasTrackedInChunk = false;
            for (BlockPos p : placedEggs) {
                if (p.world().equals(cp.world()) && p.chunkX() == cp.cx() && p.chunkZ() == cp.cz()) {
                    hasTrackedInChunk = true;
                    break;
                }
            }
            Long lastScan = scannedChunks.get(cp);
            if (hasTrackedInChunk && lastScan != null && now - lastScan < 60_000L) {
                continue;
            }

            int minY = world.getMinHeight();
            int maxY = world.getMaxHeight();
            int baseX = cp.cx() << 4;
            int baseZ = cp.cz() << 4;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = minY; y < maxY; y++) {
                        if (chunk.getBlock(x, y, z).getType() == Material.DRAGON_EGG) {
                            BlockPos found = new BlockPos(cp.world(), baseX + x, y, baseZ + z);
                            if (placedEggs.add(found)) {
                                dirty = true;
                            }
                        }
                    }
                }
            }
            scannedChunks.put(cp, now);
        }

        if (scannedChunks.size() > 4096) {
            scannedChunks.entrySet().removeIf(e -> now - e.getValue() > 120_000L);
        }

        if (dirty) {
            plugin.saveData();
        }
    }

    public int spawnParticlesPulse() {
        if (Bukkit.getOnlinePlayers().isEmpty() || eligibleParticles.isEmpty()) return 0;
        refreshEggsAroundOnlinePlayers();

        List<Location> centers = new ArrayList<>();

        // Поставленные блоки яиц в загруженных чанках рядом с игроками
        for (BlockPos pos : placedEggs) {
            World world = Bukkit.getWorld(pos.world());
            if (world == null || !world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) continue;
            Location center = new Location(world, pos.x() + 0.5, pos.y() + 0.65, pos.z() + 0.5);
            if (hasNearbyOnlinePlayer(center, 96.0)) {
                centers.add(center);
            }
        }

        // Игроки, держащие яйцо дракона
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ignoreSpectator && player.getGameMode() == GameMode.SPECTATOR) continue;
            if (isHoldingEgg(player)) {
                centers.add(player.getLocation().add(0.0, 1.0, 0.0));
            }
        }

        // Выброшенные предметы-яйца дракона
        if (includeDroppedItems) {
            for (Location itemLoc : findNearbyDroppedEggs(radius + 32.0)) {
                centers.add(itemLoc.add(0.0, 0.35, 0.0));
            }
        }

        for (Location center : centers) {
            spawnRandomParticle(center, particleCount);
        }
        return centers.size();
    }

    public int triggerEffectsPulse() {
        if (Bukkit.getOnlinePlayers().isEmpty() || eligibleEffects.isEmpty()) return 0;
        refreshEggsAroundOnlinePlayers();

        Map<World, List<Location>> eggsByWorld = new HashMap<>();

        for (BlockPos pos : placedEggs) {
            World world = Bukkit.getWorld(pos.world());
            if (world == null || !world.isChunkLoaded(pos.chunkX(), pos.chunkZ())) continue;
            eggsByWorld.computeIfAbsent(world, k -> new ArrayList<>())
                    .add(new Location(world, pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5));
        }

        if (heldAffectsNearby) {
            for (Player holder : Bukkit.getOnlinePlayers()) {
                if (ignoreSpectator && holder.getGameMode() == GameMode.SPECTATOR) continue;
                if (isHoldingEgg(holder)) {
                    eggsByWorld.computeIfAbsent(holder.getWorld(), k -> new ArrayList<>())
                            .add(holder.getLocation().add(0.0, 1.0, 0.0));
                }
            }
        }

        if (includeDroppedItems) {
            for (Location itemLoc : findNearbyDroppedEggs(radius + 8.0)) {
                eggsByWorld.computeIfAbsent(itemLoc.getWorld(), k -> new ArrayList<>())
                        .add(itemLoc);
            }
        }

        double radiusSq = radius * radius;
        int durationTicks = effectDurationSeconds * 20;
        var rng = ThreadLocalRandom.current();
        int affected = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ignoreSpectator && player.getGameMode() == GameMode.SPECTATOR) continue;

            boolean qualifies = isHoldingEgg(player);
            if (!qualifies) {
                List<Location> worldEggs = eggsByWorld.get(player.getWorld());
                if (worldEggs != null) {
                    Location feet = player.getLocation();
                    Location eyes = player.getEyeLocation();
                    for (Location eggLoc : worldEggs) {
                        if (eggLoc.distanceSquared(feet) <= radiusSq
                                || eggLoc.distanceSquared(eyes) <= radiusSq) {
                            qualifies = true;
                            break;
                        }
                    }
                }
            }
            if (!qualifies) continue;

            PotionEffectType type = eligibleEffects.get(rng.nextInt(eligibleEffects.size()));
            int level = minLevel + rng.nextInt(maxLevel - minLevel + 1);
            player.addPotionEffect(new PotionEffect(type, durationTicks, level - 1, false, true, true));
            if (actionbarNotify) {
                player.sendActionBar(Msg.color("&5✦ Яйцо дракона: &d"
                        + Msg.effectName(type) + " " + Msg.toRoman(level)
                        + " &7(" + effectDurationSeconds + "с)"));
            }
            affected++;
        }
        return affected;
    }

    private boolean isHoldingEgg(Player player) {
        var inv = player.getInventory();
        if (inv.getItemInMainHand().getType() == Material.DRAGON_EGG
                || inv.getItemInOffHand().getType() == Material.DRAGON_EGG) {
            return true;
        }
        if (!checkFullInventory) return false;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == Material.DRAGON_EGG) {
                return true;
            }
        }
        return false;
    }

    private List<Location> findNearbyDroppedEggs(double checkRadius) {
        Set<UUID> seen = new HashSet<>();
        List<Location> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Entity entity : player.getNearbyEntities(checkRadius, checkRadius, checkRadius)) {
                if (!(entity instanceof Item item)) continue;
                if (item.getItemStack().getType() != Material.DRAGON_EGG) continue;
                if (seen.add(item.getUniqueId())) {
                    out.add(item.getLocation());
                }
            }
        }
        return out;
    }

    private boolean hasNearbyOnlinePlayer(Location loc, double maxDistance) {
        World world = loc.getWorld();
        if (world == null) return false;
        double maxSq = maxDistance * maxDistance;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= maxSq) {
                return true;
            }
        }
        return false;
    }

    private void spawnRandomParticle(Location loc, int count) {
        World world = loc.getWorld();
        if (world == null || eligibleParticles.isEmpty()) return;
        var rng = ThreadLocalRandom.current();
        Particle p = eligibleParticles.get(rng.nextInt(eligibleParticles.size()));
        try {
            Class<?> dt = p.getDataType();
            if (dt == Void.class) {
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02);
            } else if (dt == Particle.DustOptions.class) {
                Color c = Color.fromRGB(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02, new Particle.DustOptions(c, 1.3f));
            } else if (dt == Particle.DustTransition.class) {
                Color c1 = Color.fromRGB(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
                Color c2 = Color.fromRGB(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02, new Particle.DustTransition(c1, c2, 1.3f));
            } else if (dt == Color.class) {
                Color c = Color.fromRGB(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02, c);
            } else if (dt == BlockData.class) {
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02, eggData());
            } else if (dt == ItemStack.class) {
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02, new ItemStack(Material.DRAGON_EGG));
            } else if (dt == Float.class) {
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02, 0.0f);
            } else if (dt == Integer.class) {
                world.spawnParticle(p, loc, count, 0.65, 0.65, 0.65, 0.02, 0);
            }
        } catch (IllegalArgumentException ignored) {
            world.spawnParticle(Particle.PORTAL, loc, count, 0.65, 0.65, 0.65, 0.03);
        }
    }
}

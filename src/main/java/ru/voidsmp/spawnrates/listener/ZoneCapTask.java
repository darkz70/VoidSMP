package ru.voidsmp.spawnrates.listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import ru.voidsmp.spawnrates.VoidSpawnRatesPlugin;
import ru.voidsmp.spawnrates.config.PluginSettings;
import ru.voidsmp.spawnrates.storage.ZoneRecord;
import ru.voidsmp.spawnrates.zone.Zone;

/**
 * Следит за лимитом мобов в зонах (cap) и, если у зоны включено
 * remove-excess, убирает лишних.
 *
 * <p>Считать мобы приходится периодически, а не на каждом спавне: обход
 * чанков зоны — единственная приемлемая по цене операция. Событие спавна при
 * этом видит уже готовое число из {@code zone.runtime()}, поэтому решение
 * принимается без аллокаций и без обращения к миру.</p>
 */
public final class ZoneCapTask implements Runnable {

    private final VoidSpawnRatesPlugin plugin;
    private int tickCounter;

    public ZoneCapTask(VoidSpawnRatesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        PluginSettings settings = plugin.settings();
        tickCounter++;
        for (Zone zone : plugin.registry().all()) {
            ZoneRecord record = zone.record();
            if (!record.enabled() || (!record.hasCap() && !record.removeExcess())) {
                continue;
            }
            World world = Bukkit.getWorld(record.world());
            if (world == null) {
                zone.runtime().setMobs(-1, false, tickCounter);
                continue;
            }
            List<Player> nearby = playersNear(world, zone, record, settings.playerDistance);
            if (settings.skipWhenNoPlayersNearby && nearby.isEmpty()) {
                continue;
            }
            CountResult result = count(world, zone, record, settings);
            zone.runtime().setMobs(result.count, result.complete, tickCounter);

            if (record.removeExcess() && result.complete && record.hasCap()
                    && result.count > record.cap()) {
                removeExcess(zone, result.candidates, result.count - record.cap(), nearby, settings);
            }
        }
    }

    private static final class CountResult {
        private int count;
        private boolean complete = true;
        private final List<LivingEntity> candidates = new ArrayList<>();
    }

    private CountResult count(World world, Zone zone, ZoneRecord record, PluginSettings settings) {
        CountResult result = new CountResult();
        for (int chunkX = zone.minChunkX(); chunkX <= zone.maxChunkX(); chunkX++) {
            for (int chunkZ = zone.minChunkZ(); chunkZ <= zone.maxChunkZ(); chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    result.complete = false;
                    continue;
                }
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                if (!chunk.isEntitiesLoaded()) {
                    result.complete = false;
                    continue;
                }
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof LivingEntity living)) {
                        continue;
                    }
                    if (!isCountable(living, record, settings)) {
                        continue;
                    }
                    Location location = living.getLocation();
                    if (!record.containsBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                        continue;
                    }
                    result.count++;
                    if (record.removeExcess()) {
                        result.candidates.add(living);
                    }
                }
            }
        }
        return result;
    }

    private boolean isCountable(LivingEntity entity, ZoneRecord record, PluginSettings settings) {
        if (entity.getType() == null || !entity.isValid() || entity.isDead()) {
            return false;
        }
        SpawnCategory category = entity.getType().getSpawnCategory();
        if (category == SpawnCategory.MISC || !record.matchesCategory(category.name())) {
            return false;
        }
        if (settings.protectNamedMobs && entity.getCustomName() != null) {
            return false;
        }
        if (settings.protectRiders && entity.getVehicle() != null) {
            return false;
        }
        return true;
    }

    private void removeExcess(Zone zone, List<LivingEntity> candidates, int excess,
                              List<Player> nearby, PluginSettings settings) {
        int toRemove = Math.min(excess, settings.maxRemovalPerTick);
        if (toRemove <= 0 || candidates.isEmpty()) {
            return;
        }
        List<Ranked> ranked = new ArrayList<>(candidates.size());
        for (LivingEntity entity : candidates) {
            if (!entity.isValid() || entity.isDead()) {
                continue;
            }
            ranked.add(new Ranked(entity, distanceToPlayers(entity.getLocation(), nearby, zone)));
        }
        // Убираем с краёв зоны: то, что дальше всего от игроков, — меньше шансов
        // задеть моба, которого как раз видят.
        ranked.sort(Comparator.comparingDouble((Ranked entry) -> entry.distance).reversed());
        int removed = 0;
        for (Ranked entry : ranked) {
            if (removed >= toRemove) {
                break;
            }
            if (entry.entity.remove()) {
                removed++;
            }
        }
        if (removed > 0) {
            zone.runtime().recordRemoved(removed);
            zone.runtime().bumpMobs(-removed);
            if (settings.debug) {
                plugin.getLogger().info("[cap] зона " + zone.name() + ": убрано лишних мобов — " + removed);
            }
        }
    }

    private static final class Ranked {
        private final LivingEntity entity;
        private final double distance;

        private Ranked(LivingEntity entity, double distance) {
            this.entity = entity;
            this.distance = distance;
        }
    }

    private static double distanceToPlayers(Location location, List<Player> players, Zone zone) {
        double best = Double.MAX_VALUE;
        for (Player player : players) {
            Location other = player.getLocation();
            if (other.getWorld() != location.getWorld()) {
                continue;
            }
            double distance = location.distanceSquared(other);
            if (distance < best) {
                best = distance;
            }
        }
        if (best != Double.MAX_VALUE) {
            return best;
        }
        return location.distanceSquared(new Location(location.getWorld(),
                zone.centerX(), zone.centerY(), zone.centerZ()));
    }

    /** Игроки в самой зоне или в пределах player-distance от её границ. */
    private List<Player> playersNear(World world, Zone zone, ZoneRecord record, int distance) {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!world.equals(player.getWorld())) {
                continue;
            }
            Location location = player.getLocation();
            if (record.containsBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                    || zone.distanceTo(location.getBlockX(), location.getBlockY(), location.getBlockZ()) <= distance) {
                out.add(player);
            }
        }
        return out;
    }

    /**
     * Моб умер — в зоне его больше нет. Не обязательно (пересчёт всё равно
     * поправит), но так лимит освобождается быстрее и толпа не вырастает
     * между проверками.
     */
    // EntityDeathEvent не Cancellable: ignoreCancelled = true здесь уронил бы
    // регистрацию слушателя на старте плагина.
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getType() == null) {
            return;
        }
        Location location = entity.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (Zone zone : plugin.registry().zonesAt(world.getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
            ZoneRecord record = zone.record();
            if (!record.hasCap() || !record.matchesCategory(entity.getType().getSpawnCategory().name())) {
                continue;
            }
            zone.runtime().bumpMobs(-1);
        }
    }
}

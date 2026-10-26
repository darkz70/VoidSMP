package ru.voidsmp.spawnrates.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;

import ru.voidsmp.spawnrates.VoidSpawnRatesPlugin;
import ru.voidsmp.spawnrates.config.PluginSettings;
import ru.voidsmp.spawnrates.spawn.SpawnRuleEngine;
import ru.voidsmp.spawnrates.zone.Zone;

/**
 * Собственно реализация «спавн рейта».
 *
 * <p>Два слушателя, и оба нужны:</p>
 * <ul>
 *   <li>{@link PreCreatureSpawnEvent} — Paper кидает его до создания сущности.
 *     Здесь отрабатывают только детерминированные запреты (множитель 0 и
 *     достигнутый лимит), потому что отменять «половину» спавнов наугад тут
 *     нельзя: тот же выбор должен один раз решить {@link CreatureSpawnEvent}.</li>
 *   <li>{@link CreatureSpawnEvent} — страховка для всех причин, которые Paper
 *     в pre-событие не отдаёт, плюс вероятность и доспаун лишних мобов.</li>
 * </ul>
 *
 * <p>Яйца спавна, /summon, разведение и построенные големы не трогаются:
 * в список причин попадают только те, что перечислены в config.yml.</p>
 */
public final class SpawnListener implements Listener {

    private static final SpawnRuleEngine.RandomSource RANDOM =
            () -> ThreadLocalRandom.current().nextDouble();

    private final VoidSpawnRatesPlugin plugin;
    private long lastDebugLog;

    public SpawnListener(VoidSpawnRatesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreCreatureSpawn(PreCreatureSpawnEvent event) {
        PluginSettings settings = plugin.settings();
        if (!settings.enabled || plugin.registry().size() == 0) {
            return;
        }
        String reason = event.getReason().name();
        if (!settings.isRelevantReason(reason)) {
            return;
        }
        EntityType type = event.getType();
        if (type == null || !type.isAlive()) {
            return;
        }
        Location location = event.getSpawnLocation();
        if (location == null || location.getWorld() == null) {
            return;
        }
        List<Zone> zones = plugin.registry().zonesAt(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (zones.isEmpty()) {
            return;
        }
        List<Zone> applicable = filter(zones, settings.isSpawnerReason(reason));
        if (applicable == null) {
            return;
        }
        SpawnRuleEngine.Decision decision = plugin.engine()
                .evaluateHard(applicable, type.name(), type.getSpawnCategory().name());
        if (decision.deny()) {
            event.setCancelled(true);
            if (decision.abortAttempts()) {
                // Просим ваниллу не перебирать дальше точки в этом чанке:
                // дешевле, чем отменять каждую попытку по отдельности.
                event.setShouldAbortSpawn(true);
            }
            debug("pre-cancel " + type.name() + " в зоне "
                    + (decision.zone() == null ? "?" : decision.zone().name()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        PluginSettings settings = plugin.settings();
        if (!settings.enabled || plugin.registry().size() == 0) {
            return;
        }
        String reason = event.getSpawnReason().name();
        if (!settings.isRelevantReason(reason)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        EntityType type = entity.getType();
        if (!type.isAlive()) {
            return;
        }
        Location location = entity.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        List<Zone> zones = plugin.registry().zonesAt(world.getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (zones.isEmpty()) {
            return;
        }
        List<Zone> applicable = filter(zones, settings.isSpawnerReason(reason));
        if (applicable == null) {
            return;
        }

        SpawnRuleEngine engine = plugin.engine();
        SpawnRuleEngine.Decision decision = engine
                .evaluate(applicable, type.name(), type.getSpawnCategory().name(), RANDOM);
        if (decision.deny()) {
            event.setCancelled(true);
            debug("cancel " + type.name() + " в зоне "
                    + (decision.zone() == null ? "?" : decision.zone().name()));
            return;
        }
        Zone zone = decision.zone();
        if (zone != null) {
            // Счётчик лимита обновляется раз в несколько тиков — подправим его
            // сразу, иначе за окно между пересчётами набегает лишняя толпа.
            zone.runtime().bumpMobs(1);
        }
        if (decision.extraSpawns() > 0 && zone != null) {
            spawnExtra(world, location, type, decision.extraSpawns(), zone);
        }
    }

    /**
     * @return список зон, которым эта причина интересна, или null, если таких нет
     */
    private static List<Zone> filter(List<Zone> zones, boolean spawnerReason) {
        if (zones.size() == 1) {
            Zone only = zones.get(0);
            if (!only.isEnabled() || (spawnerReason && !only.affectsSpawners())) {
                return null;
            }
            return zones;
        }
        List<Zone> out = new ArrayList<>(zones.size());
        for (Zone zone : zones) {
            if (!zone.isEnabled()) {
                continue;
            }
            if (spawnerReason && !zone.affectsSpawners()) {
                continue;
            }
            out.add(zone);
        }
        return out.isEmpty() ? null : out;
    }

    /** Лишние мобы — единственный способ сделать спавн чаще, чем он есть в ванилле. */
    private void spawnExtra(World world, Location origin, EntityType type, int count, Zone zone) {
        int toSpawn = Math.min(count, Math.max(plugin.settings().maxExtraSpawns, 0));
        if (toSpawn <= 0 || !type.isSpawnable()) {
            return;
        }
        // Спавн внутри CreatureSpawnEvent менял бы список сущностей мира во время
        // обхода — переносим на следующий тик.
        Bukkit.getScheduler().runTask(plugin, () -> {
            int spawned = 0;
            for (int attempt = 0; attempt < toSpawn; attempt++) {
                if (zone.isCapReached()) {
                    break;
                }
                Location spot = findSpot(world, origin);
                if (spot == null) {
                    break;
                }
                try {
                    world.spawnEntity(spot, type);
                    spawned++;
                } catch (RuntimeException ex) {
                    break;
                }
            }
            if (spawned > 0) {
                zone.runtime().bumpMobs(spawned);
                debug("+" + spawned + " " + type.name() + " (зона " + zone.name() + ")");
            }
        });
    }

    /** Ищет рядом свободное место над твёрдым блоком — чтобы моб не рождался в стене. */
    private static Location findSpot(World world, Location origin) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int radius = 6;
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = origin.getBlockX() + random.nextInt(-radius, radius + 1);
            int z = origin.getBlockZ() + random.nextInt(-radius, radius + 1);
            int y = origin.getBlockY() + random.nextInt(-1, 2);
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
                continue;
            }
            if (!isFree(world, x, y, z) || !isFree(world, x, y + 1, z)) {
                continue;
            }
            Block below = world.getBlockAt(x, y - 1, z);
            if (below.getType().isAir()) {
                continue;
            }
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    private static boolean isFree(World world, int x, int y, int z) {
        return !world.getBlockAt(x, y, z).getType().isSolid();
    }

    private void debug(String message) {
        if (!plugin.settings().debug) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDebugLog < 200L) {
            return;
        }
        lastDebugLog = now;
        plugin.getLogger().info("[spawn] " + message);
    }
}

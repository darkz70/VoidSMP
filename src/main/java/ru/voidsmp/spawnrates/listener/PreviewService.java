package ru.voidsmp.spawnrates.listener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import ru.voidsmp.spawnrates.VoidSpawnRatesPlugin;
import ru.voidsmp.spawnrates.config.PluginSettings;
import ru.voidsmp.spawnrates.storage.ZoneRecord;
import ru.voidsmp.spawnrates.util.Args;
import ru.voidsmp.spawnrates.util.Num;
import ru.voidsmp.spawnrates.zone.Zone;
import ru.voidsmp.spawnrates.zone.ZoneRegistry;
import ru.voidsmp.spawnrates.zone.ZoneSelection;

/**
 * Показ границ зоны частицами и подпись в action bar. Косметика: свой таймер,
 * ничего не меняет в мире, между рестартами не хранит ровным счётом ничего.
 */
public final class PreviewService implements Runnable {

    /** 12 рёбер кубоида: пары из 8 углов, отличающиеся ровно одним битом. */
    private static final int[][] EDGES = buildEdges();

    private final VoidSpawnRatesPlugin plugin;
    private final Map<UUID, String> showing = new HashMap<>();
    private final Set<UUID> hudActive = new HashSet<>();

    public PreviewService(VoidSpawnRatesPlugin plugin) {
        this.plugin = plugin;
    }

    private static int[][] buildEdges() {
        int[][] edges = new int[12][2];
        int index = 0;
        for (int corner = 0; corner < 8; corner++) {
            for (int bit = 1; bit < 8; bit <<= 1) {
                int other = corner | bit;
                if (other < 8 && index < edges.length) {
                    edges[index][0] = corner;
                    edges[index][1] = other;
                    index++;
                }
            }
        }
        return edges;
    }

    public void show(Player player, Zone zone) {
        showing.put(player.getUniqueId(), zone.name());
    }

    public boolean hide(Player player) {
        boolean had = showing.remove(player.getUniqueId()) != null;
        hudActive.remove(player.getUniqueId());
        if (had) {
            player.sendActionBar(Component.empty());
        }
        return had;
    }

    public boolean isShowing(Player player) {
        return showing.containsKey(player.getUniqueId());
    }

    public void forget(UUID player) {
        showing.remove(player);
        hudActive.remove(player);
    }

    public void clearAll() {
        showing.clear();
        hudActive.clear();
    }

    @Override
    public void run() {
        PluginSettings settings = plugin.settings();
        if (showing.isEmpty() && !settings.hud) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            String zoneName = showing.get(player.getUniqueId());
            if (zoneName != null) {
                Zone zone = plugin.registry().byName(zoneName);
                if (zone == null) {
                    showing.remove(player.getUniqueId());
                } else {
                    drawBox(player, zone.record());
                }
            }
            if (settings.hud) {
                hud(player);
            }
        }
    }

    /** Разовый показ рамки сразу после клика палкой — пока зона ещё не создана. */
    public void drawBoxOnce(Player player, ZoneSelection selection) {
        if (!selection.isComplete()) {
            return;
        }
        drawBox(player, selection.world(),
                selection.minX(), selection.minY(), selection.minZ(),
                selection.maxX(), selection.maxY(), selection.maxZ());
    }

    private void drawBox(Player player, ZoneRecord record) {
        drawBox(player, record.world(), record.minX(), record.minY(), record.minZ(),
                record.maxX(), record.maxY(), record.maxZ());
    }

    private void drawBox(Player player, String worldName,
                         int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        World world = player.getWorld();
        if (!world.getName().equalsIgnoreCase(worldName)) {
            return;
        }
        PluginSettings settings = plugin.settings();
        Particle particle = settings.particle;
        double limitSquared = (double) settings.previewDistance * settings.previewDistance;
        Location eye = player.getLocation();
        int points = Math.max(2, settings.pointsPerEdge);

        for (int[] edge : EDGES) {
            double[] from = corner(minX, minY, minZ, maxX, maxY, maxZ, edge[0]);
            double[] to = corner(minX, minY, minZ, maxX, maxY, maxZ, edge[1]);
            double span = Math.max(Math.abs(to[0] - from[0]),
                    Math.max(Math.abs(to[1] - from[1]), Math.abs(to[2] - from[2])));
            if (span < 1.0D) {
                continue;
            }
            int steps = (int) Math.min(points, Math.max(2.0D, span));
            for (int step = 0; step <= steps; step++) {
                double ratio = (double) step / (double) steps;
                double x = from[0] + (to[0] - from[0]) * ratio;
                double y = from[1] + (to[1] - from[1]) * ratio;
                double z = from[2] + (to[2] - from[2]) * ratio;
                double dx = x - eye.getX();
                double dy = y - eye.getY();
                double dz = z - eye.getZ();
                if (dx * dx + dy * dy + dz * dz > limitSquared) {
                    continue;
                }
                player.spawnParticle(particle, x + 0.5D, y + 0.5D, z + 0.5D, 1);
            }
        }
    }

    private static double[] corner(int minX, int minY, int minZ,
                                   int maxX, int maxY, int maxZ, int index) {
        return new double[]{
                (index & 1) == 0 ? minX : maxX,
                (index & 2) == 0 ? minY : maxY,
                (index & 4) == 0 ? minZ : maxZ};
    }

    private void hud(Player player) {
        ZoneRegistry registry = plugin.registry();
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        List<Zone> zones = registry.zonesAt(world.getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Zone zone = null;
        for (Zone candidate : zones) {
            if (candidate.isEnabled()) {
                zone = candidate;
                break;
            }
        }
        if (zone == null) {
            if (hudActive.remove(player.getUniqueId())) {
                player.sendActionBar(Component.empty());
            }
            return;
        }
        hudActive.add(player.getUniqueId());
        ZoneRecord record = zone.record();
        Args args = Args.of()
                .put("name", zone.name())
                .put("multiplier", record.multiplier() <= 0.0D
                        ? "запрещён" : Num.multiplier(record.multiplier()))
                .put("cap", record.hasCap() ? String.valueOf(record.cap()) : "");
        player.sendActionBar(plugin.messages().format("preview.hud-line", args.asMap()));
    }
}

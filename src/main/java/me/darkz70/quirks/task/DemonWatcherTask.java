package me.darkz70.quirks.task;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * «Демон азарта» (пассивный фон): изредка вдали от игроков (25–50 блоков)
 * возникает тёмный силуэт в два блока ростом со скалковыми И АДСКИМИ
 * частицами, стоит несколько секунд и растворяется.
 *
 * Никаких сообщений в чат, звуки — только тихие, по месту силуэта.
 * Настраивается: demon.enabled (вкл/выкл), demon.interval-minutes
 * (5/10/15/20/30/60), demon.min-distance, demon.max-distance.
 */
public final class DemonWatcherTask extends BukkitRunnable {

    /** Допустимые интервалы появления (минуты), переключаются в меню /gaid. */
    public static final int[] INTERVALS_MIN = {5, 10, 15, 20, 30, 60};

    private final VoidQuirksPlugin plugin;
    /** Активные силуэты. */
    private final Map<UUID, Silhouette> active = new ConcurrentHashMap<>();
    private long nextAttemptAt = 0;

    public DemonWatcherTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private record Silhouette(Location loc, long dieAt) { }

    @Override
    public void run() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("demon.enabled", false)) {
            active.clear();
            return;
        }
        long now = System.currentTimeMillis();

        // 1) живые силуэты: тёмный столб ~2 блока — скалка И ад
        active.entrySet().removeIf(e -> {
            Silhouette s = e.getValue();
            if (now >= s.dieAt) {
                // растворение: адские искры + дым, тихо
                s.loc.getWorld().spawnParticle(Particle.END_ROD, s.loc.clone().add(0, 1, 0),
                        14, 0.3, 0.6, 0.3, 0.02);
                s.loc.getWorld().spawnParticle(Particle.LAVA, s.loc.clone().add(0, 1, 0),
                        8, 0.3, 0.5, 0.3, 0.0);
                s.loc.getWorld().playSound(s.loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.7f);
                return true;
            }
            var w = s.loc.getWorld();
            // скалковая половина
            w.spawnParticle(Particle.SCULK_SOUL, s.loc.clone().add(0, 0.5, 0), 3, 0.18, 0.3, 0.18, 0.005);
            w.spawnParticle(Particle.SMOKE, s.loc.clone().add(0, 1.4, 0), 3, 0.16, 0.3, 0.16, 0.005);
            w.spawnParticle(Particle.SCULK_SOUL, s.loc.clone().add(0, 1.9, 0), 1, 0.1, 0.05, 0.1, 0.0);
            // адская половина: душевое пламя внизу, обычные искры по бокам, редкие лавовые капли
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, s.loc.clone().add(0, 0.15, 0), 2, 0.2, 0.1, 0.2, 0.002);
            w.spawnParticle(Particle.FLAME, s.loc.clone().add(0, 1.1, 0), 2, 0.25, 0.4, 0.25, 0.004);
            w.spawnParticle(Particle.ASH, s.loc.clone().add(0, 1.6, 0), 3, 0.3, 0.4, 0.3, 0.002);
            if (ThreadLocalRandom.current().nextInt(4) == 0) {
                w.spawnParticle(Particle.DRIPPING_LAVA, s.loc.clone().add(0, 2.1, 0), 1, 0.1, 0.05, 0.1, 0.0);
            }
            return false;
        });

        // 2) новые появления — раз в demon.interval-minutes (по текущему конфигу)
        if (now < nextAttemptAt) return;
        int intervalMin = cfg.getInt("demon.interval-minutes", 10);
        nextAttemptAt = now + intervalMin * 60_000L;

        double chance = cfg.getDouble("demon.chance", 0.6);
        int minD = cfg.getInt("demon.min-distance", 25);
        int maxD = cfg.getInt("demon.max-distance", 50);
        int minLife = cfg.getInt("demon.min-lifetime-seconds", 6) * 1000;
        int maxLife = cfg.getInt("demon.max-lifetime-seconds", 10) * 1000;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ThreadLocalRandom.current().nextDouble() >= chance) continue;
            Location around = player.getLocation();
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(minD, maxD);
            Location spot = around.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            spot.setY(spot.getWorld().getHighestBlockYAt(spot) + 1.0);
            long life = ThreadLocalRandom.current().nextLong(minLife, Math.max(minLife + 1, maxLife + 1));
            active.put(UUID.randomUUID(), new Silhouette(spot, now + life));
            // тихий звук появления — слышат только те, кто рядом с силуэтом; чата нет
            spot.getWorld().playSound(spot, Sound.ENTITY_ENDERMAN_AMBIENT, 0.5f, 0.5f);
            spot.getWorld().playSound(spot, Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 0.6f);
        }
    }

    /** Цикл интервала: 5 → 10 → 15 → 20 → 30 → 60 → 5 … (возвращает новое значение). */
    public static int nextInterval(int current) {
        for (int i = 0; i < INTERVALS_MIN.length; i++) {
            if (INTERVALS_MIN[i] == current) return INTERVALS_MIN[(i + 1) % INTERVALS_MIN.length];
        }
        return INTERVALS_MIN[0];
    }
}

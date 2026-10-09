package me.darkz70.quirks.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Лёгкие пасхалочные анимации частиц: кольца, всплески, вихри. */
public final class Anims {

    private Anims() { }

    /** Горизонтальное кольцо частиц радиусом r. */
    public static void ring(Location center, Particle particle, double r, int count, double speed) {
        World world = center.getWorld();
        if (world == null) return;
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            Location point = center.clone().add(Math.cos(angle) * r, 0.2, Math.sin(angle) * r);
            world.spawnParticle(particle, point, 1, 0, 0, 0, speed);
        }
    }

    /** Всплеск (огонек/магия): sphere + звук-кёрл. */
    public static void burst(Location point, Particle particle, int count) {
        World world = point.getWorld();
        if (world == null) return;
        world.spawnParticle(particle, point, count, 0.3, 0.5, 0.3, 0.04);
        world.spawnParticle(Particle.END_ROD, point, Math.max(2, count / 6), 0.2, 0.3, 0.2, 0.01);
    }

    /** Вихрь-смерч вокруг точки (по тику вокруг). */
    public static void tornado(Plugin plugin, Location center, Particle particle, int ticks,
                               double radius, double height) {
        World world = center.getWorld();
        if (world == null) return;
        new org.bukkit.scheduler.BukkitRunnable() {

            int tick = 0;

            @Override
            public void run() {
                if (tick >= ticks || !center().isChunkLoaded()) {
                    cancel();
                    return;
                }
                double t = (double) tick / ticks;
                double r = radius * (1 + t * 1.4);
                double y = height * t;
                for (int i = 0; i < 3; i++) {
                    double angle = (2 * Math.PI * (i * 120 + tick * 22)) / 360;
                    Location point = center().add(Math.cos(angle) * r, y * (i + 1) / 4.0 + 0.2,
                            Math.sin(angle) * r);
                    world.spawnParticle(particle, point, 1, 0, 0, 0, 0.02);
                }
                tick++;
            }

            private Location center() {
                return center;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Кольцо + бурст (стандартный «старт каста»). */
    public static void castStart(Location center, Particle particle) {
        ring(center, particle, 1.2, 24, 0.02);
        burst(center.clone().add(0, 1, 0), particle, 10);
    }

    /** Асинхронно-безопасная обёртка: ничего, если сервер обломился. */
    public static void silence(Runnable run) {
        try {
            if (Bukkit.isPrimaryThread()) {
                run.run();
            } else {
                Bukkit.getGlobalRegionScheduler().execute(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(Anims.class), run);
            }
        } catch (Throwable ignored) {
            // частицы — бонус, не падаем
        }
    }
}

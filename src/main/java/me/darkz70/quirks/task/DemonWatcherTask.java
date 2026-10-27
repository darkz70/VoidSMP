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
 * «Демон азарта» — паранормальный силуэт в углу зрения.
 *
 * Каждые N секунд с шансом X у игрока в 4–8 блоках возникает тёмный силуэт
 * ~2 блока высотой, стоит 3–6 секунд и растворяется в искрах.
 * Ложка прикольного: если игрок смотрит прямо на силуэт — тот шарахаетя
 * и исчезает раньше, оставляя клуб дыма и тихий шёпот.
 */
public final class DemonWatcherTask extends BukkitRunnable {

    private final VoidQuirksPlugin plugin;
    /** Активные силуэты: uuid силуэта -> состояние. */
    private final Map<UUID, Silhouette> active = new ConcurrentHashMap<>();

    public DemonWatcherTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private record Silhouette(Location loc, Location base, long bornAt, long dieAt, UUID target) { }

    private long nextAttemptAt = 0;

    @Override
    public void run() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("demon.enabled", true)) return;
        long now = System.currentTimeMillis();
        double chance = cfg.getDouble("demon.chance", 0.35);
        int minD = cfg.getInt("demon.min-distance", 4);
        int maxD = cfg.getInt("demon.max-distance", 8);
        int minLife = cfg.getInt("demon.min-lifetime-seconds", 3) * 1000;
        int maxLife = cfg.getInt("demon.max-lifetime-seconds", 6) * 1000;

        // 1) умирающие силуэты — растворяем
        active.entrySet().removeIf(e -> {
            Silhouette s = e.getValue();
            Player watched = Bukkit.getPlayer(s.target);
            boolean lookedAt = watched != null && watched.isOnline() && isLookingAt(watched, s.loc);
            if (now >= s.dieAt || lookedAt || watched == null) {
                fadeOut(s, watched, lookedAt);
                return true;
            }
            // живой силуэт: тёмный «столб» ~2 блока
            s.loc.getWorld().spawnParticle(Particle.SCULK_SOUL, s.loc.clone().add(0, 0.5, 0),
                    3, 0.18, 0.3, 0.18, 0.005);
            s.loc.getWorld().spawnParticle(Particle.SMOKE, s.loc.clone().add(0, 1.4, 0),
                    3, 0.16, 0.3, 0.16, 0.005);
            s.loc.getWorld().spawnParticle(Particle.SCULK_SOUL, s.loc.clone().add(0, 1.9, 0),
                    1, 0.1, 0.05, 0.1, 0.0); // «глаза»
            return false;
        });

        // 2) новые появления — только по расписанию (силуэты в §1 рисуются каждый тик таска)
        if (now < nextAttemptAt) return;
        nextAttemptAt = now + cfg.getInt("demon.period-seconds", 10) * 1000L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ThreadLocalRandom.current().nextDouble() >= chance) continue;
            // один силуэт на игрока
            boolean already = active.values().stream().anyMatch(s -> s.target.equals(player.getUniqueId()));
            if (already) continue;

            Location around = player.getLocation();
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(minD, maxD);
            Location spot = around.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            spot.setY(spot.getWorld().getHighestBlockYAt(spot) + 1.0);
            long life = ThreadLocalRandom.current().nextLong(minLife, Math.max(minLife + 1, maxLife + 1));
            active.put(UUID.randomUUID(),
                    new Silhouette(spot, spot.clone(), now, now + life, player.getUniqueId()));
            // тихий звук появления — слышит только жертва
            player.playSound(spot, Sound.ENTITY_ENDERMAN_AMBIENT, 0.4f, 0.55f);
        }
    }

    /** Смотрит ли игрок примерно на точку (порог ~15°). */
    private static boolean isLookingAt(Player player, Location target) {
        var eye = player.getEyeLocation().toVector();
        var dir = player.getEyeLocation().getDirection().normalize();
        var to = target.toVector().add(new org.bukkit.util.Vector(0, 1.0, 0)).subtract(eye).normalize();
        return dir.dot(to) > 0.965;
    }

    /** Исчезновение: вспышка искр + дым; если замечен — обидный шёпот. */
    private void fadeOut(Silhouette s, Player watched, boolean spotted) {
        s.loc.getWorld().spawnParticle(Particle.END_ROD, s.loc.clone().add(0, 1, 0),
                24, 0.3, 0.6, 0.3, 0.02);
        s.loc.getWorld().spawnParticle(Particle.SMOKE, s.loc.clone().add(0, 1, 0),
                20, 0.35, 0.7, 0.35, 0.01);
        if (watched != null && watched.isOnline()) {
            if (spotted) {
                watched.playSound(s.loc, Sound.ENTITY_ENDERMAN_SCREAM, 0.35f, 1.5f);
                me.darkz70.quirks.util.Msg.send(watched, "demon-spotted");
            } else {
                watched.playSound(s.loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.9f);
            }
            // азарт: 15% — «укус», 15% — «подарок», иначе просто ушёл
            double roll = ThreadLocalRandom.current().nextDouble();
            if (roll < 0.15) {
                watched.damage(1.0);
                watched.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.DARKNESS, 30, 0, true, false, false));
                me.darkz70.quirks.util.Msg.send(watched, "demon-bite");
            } else if (roll < 0.30) {
                watched.giveExp(3 + ThreadLocalRandom.current().nextInt(8));
                watched.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.LUCK, 600, 0, true, false, false));
                watched.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        watched.getLocation().add(0, 1, 0), 18, 0.4, 0.6, 0.4, 0.02);
                me.darkz70.quirks.util.Msg.send(watched, "demon-gift");
            }
        }
    }
}

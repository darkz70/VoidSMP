package me.darkz70.quirks.task;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

/**
 * «Демон азарта» (ЗАГЛУШКА до нормального босса): изредка вдали от игроков
 * (минимум 20 блоков) возникает тёмная фигура — невидимая броня-стойка в
 * позе с поднятыми руками, голова — скин демона (из ресурспака, заменяет
 * дефолтный Стив/Алекс), тело — тёмно-чёрная «ряса». Вокруг — ПРЕИМУЩЕСТВЕННО
 * тёмные и адские частицы (пламя душ, пепел, дым, чернильная тьма, капли
 * лавы, споры недра) — скалковых частиц больше нет. Стоит несколько секунд
 * и растворяется со вспышкой.
 *
 * Никаких сообщений в чат при пассивном появлении, звуки — только тихие,
 * по месту фигуры. Настраивается: demon.enabled (вкл/выкл),
 * demon.interval-minutes (5/10/15/20/30/60), demon.min-distance (не менее
 * 20 — ниже этого порога код сам не опустится), demon.max-distance.
 *
 * Админ может вызвать появление вручную — кнопка «Призвать демона» в
 * /gaid → Эффекты, рядом с переключателями демона (см. {@link #summonManual}).
 * Ручной вызов — ровно на месте админа, без ограничения по дистанции и без
 * сообщений в чат (ни об успехе, ни о неудаче).
 */
public final class DemonWatcherTask extends BukkitRunnable {

    /** Допустимые интервалы появления (минуты), переключаются в меню /gaid. */
    public static final int[] INTERVALS_MIN = {5, 10, 15, 20, 30, 60};

    /** Жёсткий пол дистанции появления — никогда не ближе этого, даже если в конфиге меньше. */
    public static final int HARD_MIN_DISTANCE = 20;

    private final VoidQuirksPlugin plugin;
    /** Активные силуэты. */
    private final Map<UUID, Silhouette> active = new ConcurrentHashMap<>();
    private long nextAttemptAt = 0;

    public DemonWatcherTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private record Silhouette(Location loc, ArmorStand stand, long dieAt) { }

    @Override
    public void run() {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("demon.enabled", false)) {
            active.entrySet().removeIf(e -> {
                despawn(e.getValue(), true);
                return true;
            });
            return;
        }
        long now = System.currentTimeMillis();

        // 1) живые силуэты: тёмно-адская фигура в позе демона
        active.entrySet().removeIf(e -> {
            Silhouette s = e.getValue();
            if (now >= s.dieAt) {
                despawn(s, false);
                return true;
            }
            tickAmbience(s.loc());
            return false;
        });

        // 2) новые появления — раз в demon.interval-minutes (по текущему конфигу)
        if (now < nextAttemptAt) return;
        int intervalMin = cfg.getInt("demon.interval-minutes", 10);
        nextAttemptAt = now + intervalMin * 60_000L;

        double chance = cfg.getDouble("demon.chance", 0.6);
        int minLife = cfg.getInt("demon.min-lifetime-seconds", 6) * 1000;
        int maxLife = cfg.getInt("demon.max-lifetime-seconds", 10) * 1000;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (ThreadLocalRandom.current().nextDouble() >= chance) continue;
            long life = ThreadLocalRandom.current().nextLong(minLife, Math.max(minLife + 1, maxLife + 1));
            spawnAround(player, now, life, false);
        }
    }

    /**
     * Ручной вызов демона админом (кнопка в /gaid → Эффекты). В отличие от пассивного
     * появления (не ближе HARD_MIN_DISTANCE от игроков), тут фигура встаёт РОВНО на
     * месте самого админа — дистанция не применяется. Живёт дольше обычного пассивного
     * появления, чтобы было время рассмотреть. Никаких сообщений в чат — ни об успехе,
     * ни о неудаче: появление должно быть тихим и незаметным по тексту.
     */
    public void summonManual(Player admin) {
        var cfg = plugin.getConfig();
        long life = cfg.getInt("demon.manual-lifetime-seconds", 25) * 1000L;
        Location spot = admin.getLocation().clone();
        ArmorStand stand = spawnFigure(spot);
        if (stand == null) return;
        active.put(UUID.randomUUID(), new Silhouette(spot, stand, System.currentTimeMillis() + life));
        spot.getWorld().playSound(spot, Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.6f);
        spot.getWorld().playSound(spot, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 0.5f, 0.5f);
        spot.getWorld().playSound(spot, Sound.BLOCK_PORTAL_AMBIENT, 0.7f, 0.5f);
    }

    /** Создаёт фигуру в случайной стороне от игрока, не ближе HARD_MIN_DISTANCE. Возвращает точку появления. */
    private Location spawnAround(Player player, long now, long life, boolean loud) {
        var cfg = plugin.getConfig();
        int minD = Math.max(HARD_MIN_DISTANCE, cfg.getInt("demon.min-distance", HARD_MIN_DISTANCE));
        int maxD = Math.max(minD + 5, cfg.getInt("demon.max-distance", 50));

        Location around = player.getLocation();
        double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double dist = ThreadLocalRandom.current().nextDouble(minD, maxD);
        Location spot = around.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        spot.setY(spot.getWorld().getHighestBlockYAt(spot) + 1.0);

        ArmorStand stand = spawnFigure(spot);
        if (stand == null) return null;

        active.put(UUID.randomUUID(), new Silhouette(spot, stand, now + life));

        float vol = loud ? 1.0f : 0.5f;
        spot.getWorld().playSound(spot, Sound.ENTITY_WITHER_AMBIENT, vol * 0.6f, 0.6f);
        spot.getWorld().playSound(spot, Sound.ENTITY_PIGLIN_BRUTE_ANGRY, vol * 0.5f, 0.5f);
        spot.getWorld().playSound(spot, Sound.BLOCK_PORTAL_AMBIENT, vol * 0.7f, 0.5f);
        return spot;
    }

    /** Невидимая броня-стойка в позе демона: голова — скин демона, тело — чёрная ряса. */
    private ArmorStand spawnFigure(Location spot) {
        try {
            return spot.getWorld().spawn(spot, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setBasePlate(false);
                as.setArms(true);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setSilent(true);
                as.setPersistent(false);
                as.setCollidable(false);
                as.setRightArmPose(new EulerAngle(Math.toRadians(-70), 0, Math.toRadians(-12)));
                as.setLeftArmPose(new EulerAngle(Math.toRadians(-65), 0, Math.toRadians(12)));
                as.setHeadPose(new EulerAngle(Math.toRadians(8), 0, 0));

                var eq = as.getEquipment();
                if (eq != null) {
                    // голова: обычный PLAYER_HEAD без владельца — клиент рисует дефолтный
                    // Стив/Алекс скин, который у нас в ресурспаке подменён на скин демона
                    eq.setHelmet(new ItemStack(Material.PLAYER_HEAD));
                    eq.setChestplate(darkLeather(Material.LEATHER_CHESTPLATE));
                    eq.setLeggings(darkLeather(Material.LEATHER_LEGGINGS));
                    eq.setBoots(darkLeather(Material.LEATHER_BOOTS));
                }
            });
        } catch (IllegalArgumentException | IllegalStateException ex) {
            // мир выгружен/чанк недоступен — просто пропускаем появление
            return null;
        }
    }

    private static ItemStack darkLeather(Material type) {
        ItemStack item = new ItemStack(type);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(Color.fromRGB(12, 10, 16)));
        return item;
    }

    /** Частицы «жизни» фигуры: тёмные (дым, чернильная тьма) + адские (душевое пламя, пепел, лава, споры недра). */
    private void tickAmbience(Location loc) {
        var w = loc.getWorld();
        var rnd = ThreadLocalRandom.current();

        // тёмная половина: дым и чернильная тьма
        w.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 1.3, 0), 2, 0.2, 0.4, 0.2, 0.004);
        w.spawnParticle(Particle.SMOKE, loc.clone().add(0, 0.6, 0), 3, 0.18, 0.3, 0.18, 0.003);
        if (rnd.nextInt(3) == 0) {
            w.spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 1.8, 0), 1, 0.12, 0.1, 0.12, 0.0);
        }

        // адская половина: душевое пламя у ног, искры по телу, пепел и редкая лава
        w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.1, 0), 3, 0.22, 0.1, 0.22, 0.002);
        w.spawnParticle(Particle.FLAME, loc.clone().add(0, 1.1, 0), 2, 0.25, 0.4, 0.25, 0.004);
        w.spawnParticle(Particle.ASH, loc.clone().add(0, 1.6, 0), 3, 0.3, 0.4, 0.3, 0.002);
        if (rnd.nextInt(3) == 0) {
            w.spawnParticle(rnd.nextBoolean() ? Particle.CRIMSON_SPORE : Particle.WARPED_SPORE,
                    loc.clone().add(0, 1.4, 0), 2, 0.35, 0.5, 0.35, 0.0);
        }
        if (rnd.nextInt(4) == 0) {
            w.spawnParticle(Particle.DRIPPING_LAVA, loc.clone().add(0, 2.1, 0), 1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    /** Растворение фигуры: яркая вспышка тьмы+ада, звук и удаление ArmorStand'а. */
    private void despawn(Silhouette s, boolean silent) {
        var w = s.loc().getWorld();
        if (!silent) {
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, s.loc().clone().add(0, 1, 0), 24, 0.35, 0.7, 0.35, 0.03);
            w.spawnParticle(Particle.ASH, s.loc().clone().add(0, 1, 0), 18, 0.4, 0.7, 0.4, 0.02);
            w.spawnParticle(Particle.SQUID_INK, s.loc().clone().add(0, 1.2, 0), 10, 0.3, 0.5, 0.3, 0.01);
            w.spawnParticle(Particle.LAVA, s.loc().clone().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.0);
            w.playSound(s.loc(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.7f);
            w.playSound(s.loc(), Sound.ENTITY_WITHER_HURT, 0.3f, 0.6f);
        }
        if (s.stand() != null && s.stand().isValid()) {
            s.stand().remove();
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

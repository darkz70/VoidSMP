package me.darkz70.quirks.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mob.ZayceKrol;
import me.darkz70.quirks.mob.ZayceKrol.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Wolf;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Движок зайцекролов (каждые 10 сек + раз в час):
 * фермер пашет (морковь, кд 5 мин, дом 30 блоков), охрана дерётся с монстрами и волками
 * (элективной в 2 раза сильнее), житель держится дома (15 блоков, ночью всегда у дома),
 * царь не двигается, прыгает в 2 раза выше, вечереет императора, жители растянут поколение
 * с 60 (+2 к каждой группе ежечасно). И по мелочи — фантомы боятся игроков с Котом.
 */
public final class ZayceKrolTask extends BukkitRunnable {

    private final VoidQuirksPlugin plugin;
    private final Map<UUID, Long> farmerCd = new HashMap<>();   // фермер -> время след. прополки
    private final Map<UUID, UUID> eliteAggro = new HashMap<>(); // элитный -> игрок помощи царя
    private final Map<UUID, UUID> guardAggro = new HashMap<>(); // охранник -> игрок
    private final Map<UUID, Long> tsarGreeting = new HashMap<>(); // царь -> когда молчал о императоре
    private final Map<UUID, Long> tsarSummonCd = new HashMap<>(); // царь -> когда закончится КД призыва
    private long lastHourTick = System.currentTimeMillis();

    public ZayceKrolTask(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Админ-спавн: один зайцекрол роли в точке. */
    public Rabbit spawnOne(Location at, Role role) {
        Rabbit rabbit = at.getWorld().spawn(at, Rabbit.class);
        ZayceKrol.convert(rabbit, role);
        return rabbit;
    }

    /** Админ-спавн полной колонии: царь + жители + охрана + фермер + элитный. */
    public void spawnColony(Location at) {
        spawnOne(at, Role.TSAR);
        spawnPack(at.getWorld(), at, Role.RESIDENT, 3);
        spawnPack(at.getWorld(), at, Role.GUARD, 2);
        spawnPack(at.getWorld(), at, Role.FARMER, 1);
        spawnPack(at.getWorld(), at, Role.ELITE, 1);
    }

    /** Админ-очистка: удалить ВСЕХ зайцекролов во всех мирах. Возвращает количество. */
    public int purgeAll() {
        int killed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Rabbit rabbit : world.getEntitiesByClass(Rabbit.class)) {
                if (ZayceKrol.isZayce(rabbit)) {
                    rabbit.remove();
                    killed++;
                }
            }
        }
        return killed;
    }

    /** Царь ударен: призвать 2 элитных и напасть на обидчика (императору — не дерёмся). */
    public void onTsarHit(Rabbit tsar, Player attacker) {
        if (plugin.quirks().levelOf(attacker, Quirk.BEDROCK) > 0) return; // «не действует на Бедрока»
        long now = System.currentTimeMillis();
        Long next = tsarSummonCd.get(tsar.getUniqueId());
        if (next != null && now < next) return;        // призыв — раз в 1 минуту
        tsarSummonCd.put(tsar.getUniqueId(), now + 60_000L);
        for (int i = 0; i < 2; i++) {
            Rabbit elite = tsar.getWorld().spawn(tsar.getLocation(), Rabbit.class);
            ZayceKrol.convert(elite, Role.ELITE);
            eliteAggro.put(elite.getUniqueId(), attacker.getUniqueId());
        }
        // и царь хилится 2 минуты подряд
        tsar.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.REGENERATION, 2400, 0, true, false));
    }

    public boolean hasAggro(Rabbit rabbit) {
        return eliteAggro.containsKey(rabbit.getUniqueId()) || guardAggro.containsKey(rabbit.getUniqueId());
    }

    @Override
    public void run() {
        for (World world : Bukkit.getWorlds()) {
            for (Rabbit rabbit : world.getEntitiesByClass(Rabbit.class)) {
                if (!ZayceKrol.isZayce(rabbit) || !rabbit.isValid()) continue;
                try {
                    behave(rabbit, ZayceKrol.roleOf(rabbit));
                } catch (Exception ignored) { }
            }
        }
        if (System.currentTimeMillis() - lastHourTick >= 3_600_000L) {
            lastHourTick = System.currentTimeMillis();
            hourlyGrowth();
        }
    }

    // ---------- поведение ----------

    private void behave(Rabbit rabbit, Role role) {
        switch (role) {
            case FARMER -> farmer(rabbit);
            case GUARD -> guard(rabbit, 4.0);
            case ELITE -> {
                elite(rabbit);
                guard(rabbit, 8.0); // вдвое эффективнее
            }
            case RESIDENT -> resident(rabbit);
            case TSAR -> tsar(rabbit);
            case SERVANT -> servant(rabbit);
            case DEPUTY -> deputy(rabbit);
            case REVOLUTIONER -> revolutioner(rabbit);
        }
    }

    private void farmer(Rabbit rabbit) {
        Location home = ZayceKrol.homeOf(rabbit);
        double homeRadius = plugin.getConfig().getDouble("zaycekrol.farmer-home-radius", 30);
        if (rabbit.getLocation().distanceSquared(home) > homeRadius * homeRadius) {
            walkTo(rabbit, home); // дом единственное, куда он вообще может уйти без грядки
            return;
        }
        long cdMs = plugin.getConfig().getLong("zaycekrol.farmer-cooldown-minutes", 5) * 60_000L;
        Long next = farmerCd.get(rabbit.getUniqueId());
        long nowMs = System.currentTimeMillis();
        if (next != null) {
            if (nowMs < next) return;
        } else if (Math.random() > 0.05) {
            return; // не толпимся бегать по полю
        }
        Block soil = findField(rabbit, home, homeRadius);
        if (soil == null) return;
        walkTo(rabbit, soil.getLocation().add(0.5, 1, 0.5));
        if (rabbit.getLocation().distanceSquared(soil.getLocation().add(0.5, 1, 0.5)) < 4) {
            workField(soil);
            farmerCd.put(rabbit.getUniqueId(), nowMs + cdMs);
        }
    }

    @Nullable
    private Block findField(Rabbit rabbit, Location home, double radius) {
        int r = (int) radius;
        Block found = null;
        double best = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx += 2) {
            for (int dz = -r; dz <= r; dz += 2) {
                Location at = home.clone().add(dx, 0, dz);
                if (at.distanceSquared(home) > radius * radius) continue;
                Block block = at.getWorld().getHighestBlockAt(at).getRelative(0, -1, 0);
                if (block.getType() != Material.FARMLAND) continue;
                Block crop = block.getRelative(0, 1, 0);
                // только посевы моркови или пустые грядки
                if (crop.getType() == Material.CARROTS || crop.getType().isAir()) {
                    double dist = rabbit.getLocation().distanceSquared(block.getLocation());
                    if (dist < best) {
                        best = dist;
                        found = block;
                    }
                }
            }
        }
        return found;
    }

    private void workField(Block soil) {
        Block crop = soil.getRelative(0, 1, 0);
        if (crop.getType() == Material.CARROTS && crop.getBlockData() instanceof Ageable age) {
            if (age.getAge() >= age.getMaximumAge()) {
                crop.breakNaturally();         // выкопал
                crop.setType(Material.CARROTS); // посадил
            }
        } else if (crop.getType().isAir()) {
            crop.setType(Material.CARROTS);    // посадил на пустую грядку
        }
    }

    private void guard(Rabbit rabbit, double damage) {
        Role role = ZayceKrol.roleOf(rabbit);
        UUID aggroId = role == Role.ELITE ? eliteAggro.get(rabbit.getUniqueId())
                : guardAggro.get(rabbit.getUniqueId());
        if (aggroId != null) {
            Player victim = Bukkit.getPlayer(aggroId);
            if (victim == null || !victim.isOnline()
                    || victim.getWorld() != rabbit.getWorld()) {
                eliteAggro.remove(rabbit.getUniqueId());
                guardAggro.remove(rabbit.getUniqueId());
            } else if (walkTo(rabbit, victim.getLocation())) {
                victim.damage(damage, rabbit);
                if (victim.getHealth() <= 0 || plugin.quirks().levelOf(victim, Quirk.BEDROCK) > 0) {
                    eliteAggro.remove(rabbit.getUniqueId());
                    guardAggro.remove(rabbit.getUniqueId());
                }
            }
            return;
        }
        // держится рядом со своими зайцекролами
        Rabbit friend = nearestZayce(rabbit, plugin.getConfig().getDouble("zaycekrol.guard-radius", 14));
        if (friend != null && friend.getLocation().distanceSquared(rabbit.getLocation())
                > plugin.getConfig().getDouble("zaycekrol.guard-scan", 6) * plugin.getConfig().getDouble("zaycekrol.guard-scan", 6)) {
            walkTo(rabbit, friend.getLocation());
        }
        // революционер для стражи — всегда нарошник
        for (Entity entity : rabbit.getNearbyEntities(12, 5, 12)) {
            if (entity instanceof Rabbit other && ZayceKrol.isZayce(other)
                    && ZayceKrol.roleOf(other) == Role.REVOLUTIONER) {
                walkTo(rabbit, other.getLocation());
                if (rabbit.getLocation().distanceSquared(other.getLocation()) < 2.5) {
                    other.damage(damage, rabbit);
                }
                return;
            }
        }
        // волки, лисы и коты — врождённая вражда
        for (Entity entity : rabbit.getNearbyEntities(10, 5, 10)) {
            if (entity instanceof org.bukkit.entity.Cat cat) {
                walkTo(rabbit, cat.getLocation());
                if (rabbit.getLocation().distanceSquared(cat.getLocation()) < 2.5) {
                    cat.damage(damage, rabbit);
                }
                return;
            }
            if (entity instanceof org.bukkit.entity.Fox fox) {
                walkTo(rabbit, fox.getLocation());
                if (rabbit.getLocation().distanceSquared(fox.getLocation()) < 2.5) {
                    fox.damage(damage, rabbit);
                }
                return;
            }
            if (entity instanceof Wolf wolf && !wolf.isTamed()) {
                walkTo(rabbit, wolf.getLocation());
                if (rabbit.getLocation().distanceSquared(wolf.getLocation()) < 2.5) {
                    wolf.damage(damage, rabbit);
                }
                return;
            }
        }
        repelMonsters(rabbit, damage);
    }

    private void elite(Rabbit rabbit) {
        // элитка держится при царе
        Rabbit tsar = nearestRole(rabbit, Role.TSAR, 24);
        if (tsar != null && tsar.getLocation().distanceSquared(rabbit.getLocation()) > 64) {
            walkTo(rabbit, tsar.getLocation());
        }
    }

    private void repelMonsters(Rabbit rabbit, double damage) {
        for (Entity entity : rabbit.getNearbyEntities(8, 4, 8)) {
            if (!(entity instanceof Monster monster)) continue;
            // убивает агрессивных мобов (элитка — вдвое больней)
            if (rabbit.getLocation().distanceSquared(monster.getLocation()) < 2.5) {
                monster.damage(damage, rabbit);
            }
            // а те, что подальше, — бегут сломя голову
            Vector away = monster.getLocation().toVector().subtract(rabbit.getLocation().toVector());
            away.setY(0);
            if (away.lengthSquared() > 0.01) {
                monster.setVelocity(monster.getVelocity().add(away.normalize().multiply(0.6)));
            }
            monster.setTarget(null);
        }
    }

    private void resident(Rabbit rabbit) {
        Location home = ZayceKrol.homeOf(rabbit);
        double radius = plugin.getConfig().getDouble("zaycekrol.resident-home-radius", 15);
        boolean night = rabbit.getWorld().getTime() >= 13000L && rabbit.getWorld().getTime() <= 23000L;
        if (night) {
            walkTo(rabbit, home); // ночью — строго домой
        } else if (rabbit.getLocation().distanceSquared(home) > radius * radius) {
            walkTo(rabbit, home); // дальше 15 блоков — только домой
        }
        // поколение в имени
        rabbit.customName(me.darkz70.quirks.util.Msg.color(
                "&eЗайцекрол &7· &fЖитель &7(пок. &f" + ZayceKrol.genOf(rabbit) + "&7)"));
    }

    private void tsar(Rabbit rabbit) {
        Location home = ZayceKrol.homeOf(rabbit);
        if (rabbit.getLocation().distanceSquared(home) > 1) {
            rabbit.teleport(home); // стоит на месте спавна
        }
        rabbit.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 220, 1, true, false, false)); // ×2 прыг
        // приветствие императора
        Long next = tsarGreeting.get(rabbit.getUniqueId());
        long nowMs = System.currentTimeMillis();
        if (next != null && nowMs < next) return;
        double radius = plugin.getConfig().getDouble("zaycekrol.tsar-call-radius", 30);
        Player emperor = null;
        for (Entity entity : rabbit.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player player
                    && plugin.quirks().levelOf(player, Quirk.BEDROCK) > 0) {
                emperor = player;
                break;
            }
        }
        if (emperor == null) return;
        for (Player listener : rabbit.getWorld().getPlayers()) {
            if (listener.getLocation().distanceSquared(rabbit.getLocation()) <= radius * radius) {
                listener.sendMessage(me.darkz70.quirks.util.Msg.color(
                        "&6👑 Царь: &fЗдравствуйте, император"));
            }
        }
        rabbit.getWorld().playSound(rabbit.getLocation(),
                org.bukkit.Sound.ENTITY_RABBIT_AMBIENT, 1.5f, 0.6f);
        tsarGreeting.put(rabbit.getUniqueId(), nowMs + 600_000L); // раз в 10 минут максимум
    }

    // ---------- рост колоний (каждый час) ----------

    /** Слуга: ходит рядом с царём (до 4 блоков). */
    private void servant(Rabbit rabbit) {
        Rabbit tsar = nearestRole(rabbit, Role.TSAR, 24);
        if (tsar != null && tsar.getLocation().distanceSquared(rabbit.getLocation()) > 16) {
            walkTo(rabbit, tsar.getLocation());
        }
    }

    /** Депутат: держится в округе царя (до 20 блоков), носит имя своей партии. */
    private void deputy(Rabbit rabbit) {
        Rabbit tsar = nearestRole(rabbit, Role.TSAR, 40);
        if (tsar != null && tsar.getLocation().distanceSquared(rabbit.getLocation()) > 20 * 20) {
            walkTo(rabbit, tsar.getLocation());
        }
        String party = plugin.getConfig().getString("zaycekrol.deputy-party", "Без партии");
        rabbit.customName(me.darkz70.quirks.util.Msg.color(
                "&eЗайцекрол \u00B7 &fДепутат &8(&d" + party + "&8)"));
    }

    /** Революционер: охотится на царя. */
    private void revolutioner(Rabbit rabbit) {
        Rabbit tsar = nearestRole(rabbit, Role.TSAR, 30);
        if (tsar == null) return;
        walkTo(rabbit, tsar.getLocation());
        if (rabbit.getLocation().distanceSquared(tsar.getLocation()) < 2.5) {
            tsar.damage(4.0, rabbit);
        }
    }


    private void hourlyGrowth() {
        for (World world : Bukkit.getWorlds()) {
            var residents = world.getEntitiesByClass(Rabbit.class).stream()
                    .filter(r -> ZayceKrol.isZayce(r) && ZayceKrol.roleOf(r) == Role.RESIDENT && r.isValid())
                    .toList();
            if (residents.isEmpty()) continue;
            // поколение +1 всем жителям
            for (Rabbit resident : residents) {
                ZayceKrol.genInc(resident);
            }
            // +2 к каждой группе от лица каждого «центра» жителей (не больше капа)
            int capR = plugin.getConfig().getInt("zaycekrol.cap-residents", 30);
            int capG = plugin.getConfig().getInt("zaycekrol.cap-guards", 10);
            int capE = plugin.getConfig().getInt("zaycekrol.cap-elite", 6);
            Location core = null;
            Rabbit tsar = nearestRole(residents.get(0), Role.TSAR, 80);
            core = tsar != null ? tsar.getLocation() : ZayceKrol.homeOf(residents.get(0));
            seal: {
                long countR = countRole(world, Role.RESIDENT);
                if (countR < capR) spawnPack(world, core, Role.RESIDENT, 2);
                long countG = countRole(world, Role.GUARD);
                if (countG < capG) spawnPack(world, core, Role.GUARD, 2);
                long countE = countRole(world, Role.ELITE);
                if (countE < capE) spawnPack(world, core, Role.ELITE, 2);
                break seal;
            }
        }
    }

    private long countRole(World world, Role role) {
        return world.getEntitiesByClass(Rabbit.class).stream()
                .filter(r -> ZayceKrol.isZayce(r) && ZayceKrol.roleOf(r) == role).count();
    }

    private void spawnPack(World world, Location where, Role role, int amount) {
        for (int i = 0; i < amount; i++) {
            Rabbit rabbit = world.spawn(where.clone().add((Math.random() - 0.5) * 4, 1.5, (Math.random() - 0.5) * 4), Rabbit.class);
            ZayceKrol.convert(rabbit, role);
        }
    }

    // ---------- утилити ----------

    @Nullable
    private Rabbit nearestRole(Rabbit from, Role role, double radius) {
        Rabbit best = null;
        double bestDist = radius * radius;
        for (Entity entity : from.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Rabbit other && other != from
                    && ZayceKrol.isZayce(other) && ZayceKrol.roleOf(other) == role) {
                double dist = other.getLocation().distanceSquared(from.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = other;
                }
            }
        }
        return best;
    }

    @Nullable
    private Rabbit nearestZayce(Rabbit from, double radius) {
        Rabbit best = null;
        double bestDist = radius * radius;
        for (Entity entity : from.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Rabbit other && other != from && ZayceKrol.isZayce(other)) {
                double dist = other.getLocation().distanceSquared(from.getLocation());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = other;
                }
            }
        }
        return best;
    }

    private final Set<UUID> walking = new HashSet<>();

    /** Идёт к цели (или телепортится, если далеко). true — уже рядом. */
    private boolean walkTo(Rabbit rabbit, Location to) {
        if (rabbit.getLocation().distanceSquared(to) < 2.5) return true;
        if (rabbit.getLocation().distanceSquared(to) > 30 * 30) {
            rabbit.teleport(to);
            return true;
        }
        paperPath(rabbit, to);
        return false;
    }

    private void paperPath(Rabbit rabbit, Location to) {
        if (!walking.add(rabbit.getUniqueId())) return;
        new BukkitRunnable() {
            @Override
            public void run() {
                walking.remove(rabbit.getUniqueId());
                if (!rabbit.isValid()) return;
                org.bukkit.entity.Mob mob = rabbit;
                mob.getPathfinder().moveTo(to);
            }
        }.runTaskLater(plugin, 40L);
    }
}

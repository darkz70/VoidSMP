package me.darkz70.quirks.arcana;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Общие строительные блоки заклинаний Arcana — переиспользуются для всех 12 стихий. */
final class ArcanaEffects {

    private ArcanaEffects() {}

    /** Мгновенный «удар» по ближайшей цели во взгляде игрока: урон + отталкивание + партиклы. */
    static void bolt(Player caster, Arcana element, double damage, double range) {
        Location eye = caster.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult hit = caster.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true, 0.5,
                e -> e != caster && e instanceof LivingEntity);
        Location end = hit != null ? hit.getHitPosition().toLocation(caster.getWorld())
                : eye.clone().add(dir.clone().multiply(range));
        // след частиц
        double dist = eye.distance(end);
        for (double d = 0; d < dist; d += 0.5) {
            Location p = eye.clone().add(dir.clone().multiply(d));
            caster.getWorld().spawnParticle(element.particle(), p, 1, 0, 0, 0, 0);
        }
        caster.getWorld().playSound(eye, element.sound1(), 0.8f, 1.1f);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            target.damage(damage, caster);
            Vector knock = target.getLocation().toVector().subtract(caster.getLocation().toVector())
                    .normalize().setY(0.25).multiply(0.6);
            target.setVelocity(target.getVelocity().add(knock));
        }
    }

    /** Временный щит: поглощение урона + сопротивление. */
    static void shield(Player caster, Arcana element, int seconds, int absorptionAmplifier) {
        caster.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, seconds * 20, absorptionAmplifier, false, true));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, seconds * 20, 0, false, true));
        ring(caster, element, 1.1);
        caster.getWorld().playSound(caster.getLocation(), element.sound2(), 0.9f, 1.2f);
    }

    /** Рывок прочь — защитная мобильность. */
    static void dash(Player caster, Arcana element, double power) {
        Vector dir = caster.getLocation().getDirection().normalize();
        caster.setVelocity(dir.multiply(power).setY(Math.max(0.35, dir.getY() * power)));
        ring(caster, element, 0.6);
        caster.getWorld().playSound(caster.getLocation(), element.sound1(), 1f, 1.5f);
    }

    /** Временный баф игроку. */
    static void buff(Player caster, Arcana element, PotionEffectType type, int seconds, int amplifier) {
        caster.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, true));
        ring(caster, element, 0.9);
        caster.getWorld().playSound(caster.getLocation(), element.sound2(), 0.8f, 1f);
    }

    /** Кольцо частиц вокруг каста — общий визуальный акцент. */
    static void ring(Player caster, Arcana element, double radius) {
        Location c = caster.getLocation();
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            Location p = c.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
            if (element.color() != null) {
                caster.getWorld().spawnParticle(org.bukkit.Particle.DUST, p, 1, 0, 0, 0, 0,
                        new org.bukkit.Particle.DustOptions(element.color(), 1.2f));
            } else {
                caster.getWorld().spawnParticle(element.particle(), p, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Площадной эффект вокруг игрока — урон+откидывание враждебных сущностей в радиусе. */
    static void nova(Player caster, Arcana element, double radius, double damage) {
        ring(caster, element, radius * 0.6);
        ring(caster, element, radius);
        caster.getWorld().playSound(caster.getLocation(), element.sound1(), 1.2f, 0.8f);
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity target) || e == caster) continue;
            if (target instanceof Player p && p.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
            if (damage > 0) target.damage(damage, caster);
            Vector knock = target.getLocation().toVector().subtract(caster.getLocation().toVector())
                    .normalize().setY(0.3).multiply(0.7);
            target.setVelocity(target.getVelocity().add(knock));
        }
    }
}

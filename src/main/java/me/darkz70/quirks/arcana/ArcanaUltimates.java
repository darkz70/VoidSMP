package me.darkz70.quirks.arcana;

import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Уникальное заклинание 20-го уровня каждой стихии — открывает доступ ко 2-й стихии. */
final class ArcanaUltimates {

    private ArcanaUltimates() {}

    static void cast(VoidQuirksPlugin plugin, Player caster, Arcana element) {
        switch (element) {
            case WATER -> {
                ArcanaBlockFx.will(plugin, caster, Arcana.WATER);
                ArcanaEffects.buff(caster, element, PotionEffectType.DOLPHINS_GRACE, 20, 2);
                ArcanaEffects.buff(caster, element, PotionEffectType.WATER_BREATHING, 60, 0);
            }
            case EARTH -> {
                ArcanaBlockFx.will(plugin, caster, Arcana.EARTH);
                ArcanaEffects.nova(caster, element, 8, 6);
            }
            case FIRE -> {
                ArcanaBlockFx.will(plugin, caster, Arcana.FIRE);
                ArcanaEffects.buff(caster, element, PotionEffectType.FIRE_RESISTANCE, 30, 0);
                ArcanaEffects.buff(caster, element, PotionEffectType.STRENGTH, 20, 1);
            }
            case WIND -> {
                caster.setVelocity(caster.getVelocity().setY(1.4));
                ArcanaEffects.buff(caster, element, PotionEffectType.SPEED, 20, 2);
                ArcanaBlockFx.will(plugin, caster, Arcana.WIND);
            }
            case ICE -> {
                ArcanaBlockFx.will(plugin, caster, Arcana.ICE);
                ArcanaEffects.buff(caster, element, PotionEffectType.RESISTANCE, 20, 1);
            }
            case STONE -> {
                ArcanaBlockFx.will(plugin, caster, Arcana.STONE);
                ArcanaEffects.buff(caster, element, PotionEffectType.STRENGTH, 20, 1);
            }
            case BLOOD -> {
                double healed = 0;
                for (Entity e : caster.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof LivingEntity target && e != caster) {
                        target.damage(8, caster);
                        healed += 4;
                    }
                }
                var attr = caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (healed > 0 && attr != null) caster.setHealth(Math.min(attr.getValue(), caster.getHealth() + healed));
                ArcanaEffects.ring(caster, element, 3);
            }
            case SOUL -> {
                caster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, false, true));
                for (Entity e : caster.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof LivingEntity target && e != caster) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2, false, true));
                    }
                }
                ArcanaEffects.ring(caster, element, 4);
            }
            case PLANTS -> {
                for (Entity e : caster.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof Player ally) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2, false, true));
                    }
                }
                ArcanaEffects.buff(caster, element, PotionEffectType.REGENERATION, 20, 2);
            }
            case ANIMALS -> {
                for (Entity e : caster.getNearbyEntities(10, 10, 10)) {
                    if (e instanceof org.bukkit.entity.Tameable tameable && tameable.isTamed()
                            && e instanceof LivingEntity ally) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1, false, true));
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1, false, true));
                    }
                }
                ArcanaEffects.buff(caster, element, PotionEffectType.JUMP_BOOST, 20, 2);
            }
            case LIGHT -> {
                for (Entity e : caster.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof LivingEntity target && e != caster
                            && me.darkz70.quirks.magic.MagicSystem.isUndead(target)) {
                        target.damage(12, caster);
                    }
                    if (e instanceof Player ally) {
                        ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true));
                    }
                }
                ArcanaEffects.buff(caster, element, PotionEffectType.GLOWING, 20, 0);
            }
            case DARK -> {
                for (Entity e : caster.getNearbyEntities(8, 8, 8)) {
                    if (e instanceof LivingEntity target && e != caster) {
                        target.damage(7, caster);
                        if (e instanceof Player p) {
                            p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, true));
                        }
                    }
                }
                ArcanaEffects.buff(caster, element, PotionEffectType.STRENGTH, 20, 1);
            }
        }
        caster.getWorld().playSound(caster.getLocation(), element.sound1(), 2f, 0.7f);
        caster.getWorld().playSound(caster.getLocation(), element.sound2(), 1.5f, 1.3f);
    }
}

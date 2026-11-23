package me.darkz70.quirks.arcana;

import java.util.ArrayList;
import java.util.List;
import me.darkz70.quirks.VoidQuirksPlugin;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

/**
 * «Воздействующие на блоки» заклинания (L6-8) — у каждой из 12 стихий своя физика.
 * Блочные изменения всегда временные и откатываются сами, кроме явных «разрушений».
 */
final class ArcanaBlockFx {

    private ArcanaBlockFx() {}

    private static Block targetBlock(Player caster, double range) {
        RayTraceResult hit = caster.getWorld().rayTraceBlocks(caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(), range, FluidCollisionMode.NEVER, true);
        return hit != null ? hit.getHitBlock() : null;
    }

    private static void revertLater(VoidQuirksPlugin plugin, List<Block> blocks, List<BlockData> originals, long ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < blocks.size(); i++) {
                    Block b = blocks.get(i);
                    if (b.getType() != Material.AIR && b.getType() != Material.WATER
                            && b.getType() != Material.ICE && originals.get(i).getMaterial() == Material.AIR) {
                        continue; // не затираем то, что игроки успели построить
                    }
                    b.setBlockData(originals.get(i));
                }
            }
        }.runTaskLater(plugin, ticks);
    }

    /** Малый точечный эффект (touch, ур.6). */
    static void touch(VoidQuirksPlugin plugin, Player caster, Arcana element) {
        Block target = targetBlock(caster, 6);
        World world = caster.getWorld();
        switch (element) {
            case WATER -> {
                if (target != null) extinguishAround(target, 2);
                area(world, caster.getLocation(), 2, Material.AIR); // плацебо партиклы ниже
            }
            case EARTH, STONE -> {
                if (target != null) pillar(plugin, target, element == Arcana.STONE ? Material.STONE : Material.DIRT, 2);
            }
            case FIRE -> {
                if (target != null) {
                    Block above = target.getRelative(BlockFace.UP);
                    if (above.getType() == Material.AIR) {
                        above.setType(Material.FIRE);
                        new BukkitRunnable() {
                            @Override public void run() { if (above.getType() == Material.FIRE) above.setType(Material.AIR); }
                        }.runTaskLater(plugin, 60L);
                    }
                }
                igniteNearby(caster, 3, 3);
            }
            case WIND -> ArcanaEffects.nova(caster, element, 3, 0);
            case ICE -> {
                if (target != null && (target.getType() == Material.WATER)) {
                    target.setType(Material.ICE);
                    new BukkitRunnable() { @Override public void run() { if (target.getType() == Material.ICE) target.setType(Material.WATER); } }
                            .runTaskLater(plugin, 100L);
                }
                freezeNearby(caster, 3, 2);
            }
            case BLOOD -> lifeSteal(caster, 2.5);
            case SOUL -> {
                caster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, true));
                scareNearby(caster, 3, 1);
            }
            case PLANTS -> growNearby(target != null ? target.getLocation() : caster.getLocation(), 3);
            case ANIMALS -> pacifyNearby(caster, 4);
            case LIGHT -> blindTarget(caster, 10);
            case DARK -> blindTarget(caster, 10);
        }
        ArcanaEffects.ring(caster, element, 1.5);
    }

    /** Волновой эффект (wave, ур.7). */
    static void wave(VoidQuirksPlugin plugin, Player caster, Arcana element) {
        switch (element) {
            case WATER -> { ArcanaEffects.nova(caster, element, 4, 0); pushBack(caster, 4, 1.0); }
            case EARTH, STONE -> wall(plugin, caster, element, 4);
            case FIRE -> igniteNearby(caster, 4, 5);
            case WIND -> pushBack(caster, 5, 1.4);
            case ICE -> freezeNearby(caster, 5, 4);
            case BLOOD -> drainAura(caster, 4, 1.5);
            case SOUL -> scareNearby(caster, 5, 2);
            case PLANTS -> { growNearby(caster.getLocation(), 5); entangleNearby(caster, 5); }
            case ANIMALS -> speedPack(caster, 5, 3);
            case LIGHT -> radiantPulse(caster, 5, true);
            case DARK -> curseRing(caster, 5);
        }
        ArcanaEffects.ring(caster, element, 2.5);
    }

    /** Мощный радиус (will, ур.8). */
    static void will(VoidQuirksPlugin plugin, Player caster, Arcana element) {
        switch (element) {
            case WATER -> flood(plugin, caster, 6);
            case EARTH, STONE -> spikes(plugin, caster, element, 6);
            case FIRE -> { igniteNearby(caster, 6, 8); ArcanaEffects.nova(caster, element, 6, 2); }
            case WIND -> { pushBack(caster, 7, 2.0); caster.setVelocity(caster.getVelocity().setY(0.6)); }
            case ICE -> { freezeNearby(caster, 7, 6); spikes(plugin, caster, Arcana.ICE, 6); }
            case BLOOD -> drainAura(caster, 6, 3);
            case SOUL -> { scareNearby(caster, 7, 4); caster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 100, 0, false, true)); }
            case PLANTS -> { growNearby(caster.getLocation(), 7); healAura(caster, 7); }
            case ANIMALS -> { speedPack(caster, 7, 5); pushBack(caster, 7, 1.0); }
            case LIGHT -> radiantPulse(caster, 7, true);
            case DARK -> { curseRing(caster, 7); darknessField(caster, 7); }
        }
        ArcanaEffects.ring(caster, element, 3.5);
        caster.getWorld().playSound(caster.getLocation(), element.sound1(), 1.5f, 0.7f);
    }

    // ---------- примитивы ----------

    private static void pillar(VoidQuirksPlugin plugin, Block base, Material mat, int height) {
        List<Block> blocks = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        Block cur = base.getRelative(BlockFace.UP);
        for (int i = 0; i < height; i++) {
            if (cur.getType() != Material.AIR) break;
            blocks.add(cur);
            originals.add(cur.getBlockData().clone());
            cur.setType(mat);
            cur = cur.getRelative(BlockFace.UP);
        }
        revertLater(plugin, blocks, originals, 160L);
    }

    private static void wall(VoidQuirksPlugin plugin, Player caster, Arcana element, int radius) {
        List<Block> blocks = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        Material mat = element == Arcana.STONE ? Material.COBBLESTONE : Material.DIRT;
        Location c = caster.getLocation();
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            Block b = c.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius).getBlock();
            if (b.getType() == Material.AIR) {
                blocks.add(b);
                originals.add(b.getBlockData().clone());
                b.setType(mat);
            }
        }
        revertLater(plugin, blocks, originals, 100L);
    }

    private static void spikes(VoidQuirksPlugin plugin, Player caster, Arcana element, int radius) {
        List<Block> blocks = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        Material mat = element == Arcana.ICE ? Material.PACKED_ICE : Material.COBBLED_DEEPSLATE;
        for (Entity e : caster.getNearbyEntities(radius, 3, radius)) {
            if (!(e instanceof LivingEntity target) || e == caster) continue;
            Block under = target.getLocation().getBlock().getRelative(BlockFace.DOWN);
            Block up1 = under.getRelative(BlockFace.UP);
            if (up1.getType() == Material.AIR) {
                blocks.add(up1); originals.add(up1.getBlockData().clone());
                up1.setType(mat);
            }
            target.damage(3, caster);
        }
        revertLater(plugin, blocks, originals, 60L);
    }

    private static void flood(VoidQuirksPlugin plugin, Player caster, int radius) {
        List<Block> blocks = new ArrayList<>();
        List<BlockData> originals = new ArrayList<>();
        Location c = caster.getLocation();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Block b = c.clone().add(dx, -1, dz).getBlock();
                if (b.getType() == Material.AIR) {
                    blocks.add(b); originals.add(b.getBlockData().clone());
                    b.setType(Material.WATER);
                }
            }
        }
        revertLater(plugin, blocks, originals, 100L);
        for (Entity e : caster.getNearbyEntities(radius, 2, radius)) {
            if (e instanceof LivingEntity target && e != caster) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true));
            }
        }
    }

    private static void area(World world, Location center, int radius, Material ignored) {
        // плацебо: без блок-изменений, только используется как якорь для партиклов в touch()
    }

    private static void extinguishAround(Block center, int radius) {
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = center.getRelative(dx, dy, dz);
                    if (b.getType() == Material.FIRE) b.setType(Material.AIR);
                }
    }

    private static void igniteNearby(Player caster, double radius, int seconds) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity target && e != caster) target.setFireTicks(seconds * 20);
        }
    }

    private static void freezeNearby(Player caster, double radius, int seconds) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity target && e != caster) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, seconds * 20, 2, false, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, seconds * 20, 1, false, true));
            }
        }
    }

    private static void pushBack(Player caster, double radius, double power) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e == caster) continue;
            org.bukkit.util.Vector dir = e.getLocation().toVector().subtract(caster.getLocation().toVector())
                    .normalize().setY(0.4).multiply(power);
            e.setVelocity(e.getVelocity().add(dir));
        }
    }

    private static void lifeSteal(Player caster, double amount) {
        caster.setHealth(Math.min(caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),
                caster.getHealth() + amount));
    }

    private static void drainAura(Player caster, double radius, double amount) {
        double healed = 0;
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity target && e != caster) {
                target.damage(amount, caster);
                healed += amount * 0.5;
            }
        }
        if (healed > 0) {
            var attr = caster.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            caster.setHealth(Math.min(attr.getValue(), caster.getHealth() + healed));
        }
    }

    private static void scareNearby(Player caster, double radius, int seconds) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity target && e != caster && !(target instanceof Player)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, seconds * 20, 1, false, true));
                org.bukkit.util.Vector flee = target.getLocation().toVector().subtract(caster.getLocation().toVector())
                        .normalize().multiply(0.8).setY(0.2);
                target.setVelocity(flee);
            }
        }
    }

    private static void growNearby(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return;
        int r = (int) radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Block b = center.clone().add(dx, dy, dz).getBlock();
                    if (b.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                        ageable.setAge(ageable.getMaximumAge());
                        b.setBlockData(ageable);
                    }
                }
            }
        }
    }

    private static void entangleNearby(Player caster, double radius) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity target && e != caster) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, false, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true));
            }
        }
    }

    private static void healAura(Player caster, double radius) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true));
            }
        }
    }

    private static void pacifyNearby(Player caster, double radius) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof org.bukkit.entity.Monster monster) {
                monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3, false, true));
                if (monster.getTarget() != null) monster.setTarget(null);
            }
        }
    }

    private static void speedPack(Player caster, double radius, int seconds) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Tameable tameable && tameable.isTamed()
                    && e instanceof LivingEntity ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1, false, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, seconds * 20, 1, false, true));
            }
        }
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, seconds * 20, 1, false, true));
    }

    private static void blindTarget(Player caster, double range) {
        RayTraceResult hit = caster.getWorld().rayTrace(caster.getEyeLocation(), caster.getEyeLocation().getDirection(),
                range, FluidCollisionMode.NEVER, true, 0.5, en -> en != caster && en instanceof LivingEntity);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity living) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, true));
        }
    }

    private static void radiantPulse(Player caster, double radius, boolean healAllies) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e == caster) continue;
            if (e instanceof Player ally && healAllies) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, false, true));
                for (PotionEffect eff : List.copyOf(ally.getActivePotionEffects())) {
                    if (eff.getType() == PotionEffectType.POISON || eff.getType() == PotionEffectType.WITHER
                            || eff.getType() == PotionEffectType.WEAKNESS) {
                        ally.removePotionEffect(eff.getType());
                    }
                }
            } else if (e instanceof LivingEntity target
                    && me.darkz70.quirks.magic.MagicSystem.isUndead(target)) {
                target.damage(6, caster);
            }
        }
    }

    private static void curseRing(Player caster, double radius) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof LivingEntity target && e != caster) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, false, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, false, true));
            }
        }
    }

    private static void darknessField(Player caster, double radius) {
        for (Entity e : caster.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player target && e != caster) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, true));
            } else if (e instanceof LivingEntity target && e != caster) {
                target.damage(4, caster);
            }
        }
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, true));
    }
}

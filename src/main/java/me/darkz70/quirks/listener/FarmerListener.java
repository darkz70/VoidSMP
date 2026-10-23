package me.darkz70.quirks.listener;

import java.util.Random;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Причуда «Фермер» (v0.6.0):
 * урожай x2 (1 ур.), 80% x2 / 20% x3 (2-3 ур.); запрет сырой еды (2 ур.); слабость при <5 HP (2 ур.);
 * герой деревни и рост x4 рядом (2 ур.); неубиваемая мотыга и волчья агрессия (3 ур.).
 */
public final class FarmerListener implements Listener {

    private final VoidQuirksPlugin plugin;
    private static final Random RANDOM = new Random();

    public FarmerListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.FARMER);
    }

    /** Удвоение/утроение урожая и семян со зрелых посевов. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 1) return;
        Block block = event.getBlock();
        if (!(block.getBlockData() instanceof Ageable age)) return;
        if (age.getAge() < age.getMaximumAge()) return;

        int extraStacks = lvl == 1 ? 1 : (RANDOM.nextInt(100) < 20 ? 2 : 1); // x2 или x3
        ItemStack tool = player.getInventory().getItemInMainHand();
        Location at = block.getLocation().add(0.5, 0.4, 0.5);
        for (ItemStack drop : block.getDrops(tool)) {
            int extra = drop.getAmount() * extraStacks;
            while (extra > 0) {
                ItemStack more = drop.clone();
                more.setAmount(Math.min(drop.getMaxStackSize(), extra));
                block.getWorld().dropItemNaturally(at, more);
                extra -= more.getAmount();
            }
        }
    }

    /** Запрет сырой еды (2 ур.). */
    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 2) return;
        Material eaten = event.getItem().getType();
        if (!eaten.isEdible()) return;
        if (MaterialLists.isFarmerRaw(eaten)) {
            event.setCancelled(true);
            Msg.send(player, "farmer-raw-denied");
        }
    }

    /** Мотыга не ломается (3 ур.). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToolDamage(PlayerItemDamageEvent event) {
        if (level(event.getPlayer()) < 3) return;
        if (event.getItem().getType().name().endsWith("_HOE")) {
            event.setCancelled(true);
        }
    }

    /**
     * Периодические пассивки Фермера (вызывается из EffectsTask раз в ~2 сек):
     * слабость при HP < 5 (2 ур.), рост посевов x4 (!2 ур.), волчья агрессия (3 ур.).
     */
    public static void applyPassive(VoidQuirksPlugin plugin, Player player, int level) {
        if (player.isDead()) return;

        // 3 уровень: герой деревни II (деревня его боготворит)
        if (level >= 3) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE,
                    300, 1, true, false, true));
        }

        // слабость при меньше 5 HP (2-3 ур.)
        if (level >= 2) {
            double limit = plugin.getConfig().getDouble("farmer.weakness-hp", 5.0);
            if (player.getHealth() < limit) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, false, true));
            }
            growCrops(plugin, player);
        }

    }

    /** С 1 уровня: нейтральные мобы не трогают фермера (урон зануляется). */
    @EventHandler(ignoreCancelled = true)
    public void onNeutralDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.quirks().levelOf(player, Quirk.FARMER) < 1) return;
        if (!plugin.getConfig().getBoolean("farmer.neutral-damage-blocked", true)) return;
        if (!(event.getDamager() instanceof org.bukkit.entity.LivingEntity liv)) return;
        if (liv instanceof Player) return;
        if (NEUTRAL.contains(liv.getType())) {
            event.setCancelled(true);
        }
    }

    private static final java.util.EnumSet<EntityType> NEUTRAL = java.util.EnumSet.of(
            EntityType.WOLF, EntityType.BEE, EntityType.PANDA, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.IRON_GOLEM,
            EntityType.DOLPHIN, EntityType.GOAT);

    /** Посевы в радиусе растут быстрее (~x4): шансом добавляем стадию роста. */
    private static void growCrops(VoidQuirksPlugin plugin, Player player) {
        int radius = plugin.getConfig().getInt("farmer.growth-radius", 10);
        double chance = plugin.getConfig().getInt("farmer.growth-chance-percent", 1) / 100.0;
        Location center = player.getLocation();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        var world = center.getWorld();
        if (world == null) return;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!world.isChunkLoaded((cx + dx) >> 4, (cz + dz) >> 4)) continue;
                for (int dy = -radius; dy <= radius; dy++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (!(block.getBlockData() instanceof Ageable age)) continue;
                    if (age.getAge() >= age.getMaximumAge()) continue;
                    if (RANDOM.nextDouble() >= chance) continue;
                    age.setAge(age.getAge() + 1);
                    block.setBlockData(age, false);
                }
            }
        }
    }
}

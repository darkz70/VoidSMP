package me.darkz70.quirks.listener;

import java.util.Map;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.task.EffectsTask;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.World;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Причуда «Земноводный» (v0.6.0):
 * вода даёт дыхание/скорость/силу; утопленники мирные; солнце жжёт (1-2 ур.);
 * в аду сохнет (2-3 ур.) без незеритовой брони (2 ур.).
 */
public final class AmphibianListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public AmphibianListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.AMPHIBIAN);
    }

    /** Утопленники: 1 ур. — атакуют лишь вблизи; 2-3 ур. — не нападают первыми. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Drowned drowned)) return;
        if (!(event.getTarget() instanceof Player player)) return;
        int lvl = level(player);
        if (lvl == 0) return;
        if (lvl >= 2) {
            event.setCancelled(true);
            return;
        }
        double radius = plugin.getConfig().getDouble("amphibian.drowned-aggro-radius-1", 5.0);
        if (drowned.getLocation().distanceSquared(player.getLocation()) > radius * radius) {
            event.setCancelled(true);
        }
    }

    /**
     * Периодика Земноводного (из EffectsTask, ~2 сек): бонусы воды,
     * ожог солнцем, пересыхание в аду, запрет незеритовой брони.
     */
    public static void applyPassive(VoidQuirksPlugin plugin, Player player, int level) {
        if (player.isDead()) return;
        boolean inWater = player.isInWater();

        // баффы воды
        if (inWater) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 300, 0, true, false, true));
            if (level >= 2) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 300, 0, true, false, true));
            }
            if (level >= 3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false, true));
            }
        }

        World world = player.getWorld();

        // солнце жжёт (1-2 ур.): 1 сердце в 2 секунды; шлем и вода защищают
        if (level <= 2 && !inWater && world.getEnvironment() == World.Environment.NORMAL
                && EffectsTask.isClearDay(world) && EffectsTask.exposedToSky(player)
                && player.getInventory().getHelmet() == null) {
            double damage = plugin.getConfig().getDouble("amphibian.sun-damage", 2.0);
            player.damage(damage);
        }

        // ад пересушивает (2-3 ур.): пол сердца в секунду
        if (level >= 2 && world.getEnvironment() == World.Environment.NETHER) {
            double damage = plugin.getConfig().getDouble("amphibian.nether-damage", 2.0);
            player.damage(damage);
        }

        // незеритовая броня соскальзывает (2 ур.)
        if (level == 2) {
            stripNetheriteArmor(plugin, player);
        }
    }

    private static void stripNetheriteArmor(VoidQuirksPlugin plugin, Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || !piece.getType().name().startsWith("NETHERITE_")) continue;
            armor[i] = null;
            Map<Integer, ItemStack> rest = player.getInventory().addItem(piece);
            rest.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            changed = true;
        }
        if (changed) {
            player.getInventory().setArmorContents(armor);
            if (ScanUtil.tryUse(player.getUniqueId(), "amph-armor-msg", 4000)) {
                Msg.send(player, "amph-armor-denied");
            }
        }
    }
}

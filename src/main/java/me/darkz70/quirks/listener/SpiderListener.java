package me.darkz70.quirks.listener;

import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.task.EffectsTask;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Причуда «Паук» (v0.6.0):
 * паутина не тормозит, лазание по стенам (WebPhysicsTask); 1 ур.: 18 HP + дневная слабость + мясоедение;
 * сила II в паутине (2-3 ур.); выстрел паутиной мечом Shift+ПКМ (2-3 ур.).
 */
public final class SpiderListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public SpiderListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.SPIDER);
    }

    private static boolean isSword(Material material) {
        return material.name().endsWith("_SWORD");
    }

    // ---------- дебафф: только мясо ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 1) return;
        if (MaterialLists.isMeat(event.getItem().getType())) return;
        event.setCancelled(true);
        Msg.send(player, "spider-meat-denied");
    }

    // ---------- подсказка о выстреле паутиной (2-3 ур.) ----------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 2 || !event.isSneaking()) return;
        if (isSword(player.getInventory().getItemInMainHand().getType())) {
            showHint(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (level(player) < 2 || !player.isSneaking()) return;
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item != null && isSword(item.getType())) {
            showHint(player);
        }
    }

    private void showHint(Player player) {
        if (ScanUtil.tryUse(player.getUniqueId(), "spider-hint", 4000)) {
            plugin.notifyBar(player, "spider-webs-hint");
        }
    }

    // ---------- выстрел паутиной: Shift + ПКМ мечом по блоку ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 2) return;
        if (!player.isSneaking()) return;
        if (!isSword(player.getInventory().getItemInMainHand().getType())) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        long cooldownMs = plugin.getConfig().getLong("spider.web-shot-cooldown-minutes." + Math.min(3, lvl), 10) * 60_000L;
        long remaining = ScanUtil.remaining(player.getUniqueId(), "spider-webs", cooldownMs);
        if (remaining > 0) {
            if (ScanUtil.tryUse(player.getUniqueId(), "spider-webs-msg", 1000)) {
                long seconds = (remaining + 999) / 1000;
                String time = seconds >= 60 ? ((seconds + 59) / 60) + " мин." : seconds + " с.";
                player.sendActionBar(Msg.comp("spider-webs-cooldown", "%time%", time));
            }
            return;
        }

        int needed = plugin.getConfig().getInt("spider.webs-needed", 9);
        if (countMaterial(player, Material.COBWEB) < needed) {
            Msg.send(player, "spider-webs-none");
            return;
        }

        // расходуем паутину и раскидываем 3x3 вокруг точки попадания
        player.getInventory().removeItem(new ItemStack(Material.COBWEB, needed));
        ScanUtil.stamp(player.getUniqueId(), "spider-webs");

        Block anchor = clicked.getRelative(event.getBlockFace());
        int placed = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block target = anchor.getRelative(dx, 0, dz);
                if (target.getType().isAir()) {
                    target.setType(Material.COBWEB, false);
                    placed++;
                }
            }
        }
        player.getWorld().spawnParticle(Particle.CLOUD, anchor.getLocation().add(0.5, 0.5, 0.5), 20, 0.8, 0.3, 0.8, 0.02);
        player.getWorld().playSound(anchor.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.8f, 0.7f);
        player.sendActionBar(Msg.comp("spider-webs-cast"));
    }

    private static int countMaterial(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) count += item.getAmount();
        }
        return count;
    }

    /**
     * Периодика Паука (из EffectsTask, ~2 сек): дневная слабость (1-2 ур.)
     * и сила II в паутине (2-3 ур.).
     */
    public static void applyPassive(VoidQuirksPlugin plugin, Player player, int level) {
        if (player.isDead()) return;
        boolean inWeb = WebPhysicsTask.inCobweb(player);

        // сила II, пока стоит в паутине (2-3 ур.)
        if (level >= 2 && inWeb) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false, true));
        }

        // дневная слабость (1 ур. всегда; 2 ур. — не в паутине)
        if (level <= 2 && !player.getWorld().getEnvironment().equals(org.bukkit.World.Environment.NETHER)
                && !player.getWorld().getEnvironment().equals(org.bukkit.World.Environment.THE_END)
                && EffectsTask.isClearDay(player.getWorld()) && EffectsTask.exposedToSky(player)
                && !(level == 2 && inWeb)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, false, true));
        }
    }
}

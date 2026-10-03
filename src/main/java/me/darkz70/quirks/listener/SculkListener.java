package me.darkz70.quirks.listener;

import java.util.Map;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.MaterialLists;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.ScanUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Причуда «Скалк»: двойной опыт, ограниченная еда, гниль с насыщением, +2 HP (3 ур.). */
public final class SculkListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public SculkListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** 0 = причуды нет. */
    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.SCULK);
    }

    /** Двойной опыт с мобов. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || level(killer) < 1) return;
        event.setDroppedExp(event.getDroppedExp() * 2);
    }

    /** Диета Скалка: только разрешённая пища; гнилая плоть даёт насыщение (2-3 ур.). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 1) return;

        Material eaten = event.getItem().getType();
        // зелья пьют все расы
        if (eaten == Material.POTION || eaten == Material.OMINOUS_BOTTLE) return;
        if (!MaterialLists.isSculkFood(lvl, eaten)) {
            event.setCancelled(true);
            Msg.send(player, "sculk-denied");
            return;
        }
        if (eaten == Material.ROTTEN_FLESH && lvl >= 2) {
            int ticks = lvl == 2 ? 40 : 120; // 2 сек / 6 сек
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, ticks, 0, true, false, true));
        }
    }

    /** «Необычная еда»: кости, порох, слизь — поедание правым кликом. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl < 1) return;

        Map<Material, int[]> custom = MaterialLists.sculkCustom.get(lvl);
        if (custom == null || custom.isEmpty()) return;

        ItemStack item = event.getItem();
        if (item == null) return;
        int[] values = custom.get(item.getType());
        if (values == null) return;
        if (player.getFoodLevel() >= 20) return; // сыт — просто использует предмет обычно
        if (!ScanUtil.tryUse(player.getUniqueId(), "sculk-eat", 1000)) return;

        event.setUseItemInHand(Event.Result.DENY);
        player.swingMainHand();
        item.setAmount(item.getAmount() - 1);
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + values[0]));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + values[1]));
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);
    }
}

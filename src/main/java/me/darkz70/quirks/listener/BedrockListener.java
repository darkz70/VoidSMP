package me.darkz70.quirks.listener;

import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mechanic.BedrockLogic;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Анти-обход запечатанных слотов + голодный бафф причуды «Бедрок». */
public final class BedrockListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public BedrockListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    /** 0 = причуды нет. */
    private int level(Player player) {
        return plugin.quirks().levelOf(player, Quirk.BEDROCK);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (level(player) == 0) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (BedrockLogic.isLocked(current) || BedrockLogic.isLocked(cursor)) {
            event.setCancelled(true);
            return;
        }
        // числовые клавиши — обмен со слотом хотбара
        if (event.getClick() == ClickType.NUMBER_KEY) {
            ItemStack hotbar = player.getInventory().getItem(event.getHotbarButton());
            if (BedrockLogic.isLocked(hotbar)) {
                event.setCancelled(true);
                return;
            }
        }
        // двойной клик «собрать всё» барьерами/бедроком от зажатого айтема
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && cursor != null
                && (cursor.getType() == Material.BARRIER || cursor.getType() == Material.BEDROCK)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (level(player) == 0) return;
        if (BedrockLogic.isLocked(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (level(event.getPlayer()) == 0) return;
        if (BedrockLogic.isLocked(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (level(event.getPlayer()) == 0) return;
        if (BedrockLogic.isLocked(event.getMainHandItem()) || BedrockLogic.isLocked(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (level(player) == 0) return;
        // барьеры и бедрок не выпадают
        event.getDrops().removeIf(BedrockLogic::isLocked);
        player.setItemOnCursor(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        int lvl = level(player);
        if (lvl == 0) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                BedrockLogic.applyLayout(plugin, player, lvl);
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int lvl = level(player);
        if (lvl == 0) return;
        PlayerData data = plugin.storage().get(player.getUniqueId());
        if (data == null) return;
        BedrockLogic.hungerCheck(plugin, player, data, lvl);
    }

    /** «Последний оплот» (со 2 ур.): удар до 1 HP отменяется, 5 секунд неуязвимости, кд час. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int lvl = level(player);
        if (lvl < 2) return;
        double after = player.getHealth() - event.getFinalDamage();
        if (after > plugin.getConfig().getDouble("bedrock.laststand-hp", 1.0)) return;

        PlayerData data = plugin.storage().get(player.getUniqueId());
        if (data == null) return;
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("bedrock.laststand-cooldown-minutes", 60) * 60_000L;
        if (now < data.cooldown("laststand")) return;

        data.setCooldown("laststand", now + cooldown);
        plugin.storage().save();
        event.setCancelled(true);
        int ticks = plugin.getConfig().getInt("bedrock.laststand-seconds", 5) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 4, true, false, true));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.2f);
        plugin.notify(player, "bedrock-immortal");
    }
}

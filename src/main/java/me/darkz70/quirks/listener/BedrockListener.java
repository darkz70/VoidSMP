package me.darkz70.quirks.listener;

import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mechanic.BedrockLogic;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.jetbrains.annotations.Nullable;

/** Анти-обход запечатанных слотов + голодный бафф причуды «Бедрок». */
public final class BedrockListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public BedrockListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    @Nullable
    private PlayerData bedrockData(Player player) {
        PlayerData data = plugin.quirks().data(player);
        return data != null && data.quirk() == Quirk.BEDROCK ? data : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (bedrockData(player) == null) return;

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
        if (bedrockData(player) == null) return;
        if (BedrockLogic.isLocked(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (bedrockData(event.getPlayer()) == null) return;
        if (BedrockLogic.isLocked(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (bedrockData(event.getPlayer()) == null) return;
        if (BedrockLogic.isLocked(event.getMainHandItem()) || BedrockLogic.isLocked(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (bedrockData(player) == null) return;
        // барьеры и бедрок не выпадают
        event.getDrops().removeIf(BedrockLogic::isLocked);
        player.setItemOnCursor(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        PlayerData data = bedrockData(player);
        if (data == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                BedrockLogic.applyLayout(plugin, player, data.level());
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        PlayerData data = bedrockData(player);
        if (data == null) return;
        BedrockLogic.hungerCheck(plugin, player, data);
    }
}

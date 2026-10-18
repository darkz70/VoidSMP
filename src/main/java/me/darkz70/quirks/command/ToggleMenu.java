package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Личное меню рядового игрока (без op): /quirk или /magic → выключение/включение своих
 * причуд. Причуда НЕ удаляется — она просто "спит". Больше в этом меню ничего нет.
 */
public final class ToggleMenu implements Listener {

    private static final int SIZE = 27;

    private record MenuHolder(UUID owner) implements InventoryHolder {

        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, SIZE, Component.empty());
        }
    }

    private final VoidQuirksPlugin plugin;

    public ToggleMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new MenuHolder(player.getUniqueId()), SIZE,
                Msg.color("&dПричуды: включение"));
        fill(inv, player);
        player.openInventory(inv);
    }

    private void fill(Inventory inv, Player player) {
        PlayerData data = plugin.storage().get(player.getUniqueId());
        fillBody(inv, player, data);
        List<Component> info = new ArrayList<>();
        info.add(Msg.color("&7Клик по лампе переключает причуду."));
        info.add(Msg.color("&7Серая = спит (не работает, но никуда не делась)."));
        inv.setItem(26, withMeta(new ItemStack(Material.BOOK),
                Msg.color("&fКак это работает"), info));
    }

    private void fillBody(Inventory inv, Player player, @Nullable PlayerData data) {
        inv.clear();
        if (data == null) return;
        int slot = 0;
        for (var entry : data.entries()) {
            if (slot >= 26) break;
            Quirk quirk = entry.getKey();
            boolean asleep = data.isPaused(quirk);
            Material icon = asleep ? Material.REDSTONE_TORCH : Material.LANTERN;
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.color("&7Уровень: &f" + entry.getValue()));
            lore.add(Msg.color(asleep ? "&cСПИТ &7— кликни, чтобы включить"
                    : "&aАКТИВНА &7— кликни, чтобы усыпить"));
            inv.setItem(slot++, withMeta(new ItemStack(icon), Msg.color(
                    (asleep ? "&7" : "&d") + quirk.display()), lore));
        }
        // инфо-иконка займет последний слот — ставим заново
        List<Component> info = new ArrayList<>();
        info.add(Msg.color("&7Причуда при выключении никуда не пропадает —"));
        info.add(Msg.color("&7просто перестаёт работать. И обратно — кликом."));
        inv.setItem(26, withMeta(new ItemStack(Material.BOOK),
                Msg.color("&fСон и пробуждение"), info));
    }

    private ItemStack withMeta(ItemStack item, Component name, List<Component> lore) {
        item.editMeta(meta -> {
            meta.displayName(name);
            if (!lore.isEmpty()) meta.lore(lore);
        });
        return item;
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.getUniqueId().equals(holder.owner())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 26) return;
        PlayerData data = plugin.storage().get(player.getUniqueId());
        if (data == null) return;
        int index = 0;
        for (var entry : data.entries()) {
            if (index++ == slot) {
                data.pause(entry.getKey(), !data.isPaused(entry.getKey()));
                plugin.storage().save();
                player.sendMessage(Msg.comp(data.isPaused(entry.getKey()) ? "toggle-sleep" : "toggle-awake",
                        "%quirk%", entry.getKey().display()));
                fillBody((Inventory) event.getInventory(), player, data);
                return;
            }
        }
    }
}

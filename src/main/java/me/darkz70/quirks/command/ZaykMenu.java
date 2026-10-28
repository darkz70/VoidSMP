package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * /zayk — общедоступный просмотр зайцекрольной политики: партии, родословная
 * царей, итоги выборов. Только чтение, никаких правок.
 * Иконки с нулевого слота подряд, стрелка «назад» — последним слотом.
 */
public final class ZaykMenu implements CommandExecutor, Listener {

    private final VoidQuirksPlugin plugin;

    private record Holder(String page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    public ZaykMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Msg.color("&cТолько для игроков."));
            return true;
        }
        // директива: игрокам недоступно (только OP)
        if (!player.hasPermission("quirks.admin")) {
            Msg.send(player, "no-permission");
            return true;
        }
        openRoot(player);
        return true;
    }

    public void openRoot(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("root"), 27,
                Msg.color("&eЗайцекролы: политика"));
        inv.setItem(11, btn(Material.WHITE_BANNER, "&eПартии", "&7Кто есть в игре и чем живут"));
        inv.setItem(13, btn(Material.DRAGON_HEAD, "&cЦари", "&7Текущий царь и родословная"));
        inv.setItem(15, btn(Material.PAPER, "&bВыборы", "&7Прошлые итоги голосований"));
        inv.setItem(22, btn(Material.ARROW, "&7Закрыть"));
        player.openInventory(inv);
    }

    private void openParties(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("parties"), 54,
                Msg.color("&eПартии"));
        int slot = 0;
        for (var e : plugin.politics().parties().entrySet()) {
            if (slot >= 53) break;
            String[] parts = e.getValue().split("\\|");
            boolean active = parts.length > 1 && parts[1].equals("1");
            inv.setItem(slot++, btn(active ? Material.LANTERN : Material.GRAY_DYE,
                    (active ? "&a" : "&7") + e.getKey(),
                    "&f" + parts[0],
                    active ? "&8существует" : "&8распущена"));
        }
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openTsars(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("tsars"), 54,
                Msg.color("&cЦари"));
        inv.setItem(0, btn(Material.DRAGON_HEAD, "&6&l" + plugin.politics().currentTsar(),
                "&f" + plugin.politics().currentTsarBio(),
                "&8текущий царь"));
        int slot = 1;
        for (Map<String, Object> row : plugin.politics().pastTsars()) {
            if (slot >= 53) break;
            inv.setItem(slot++, btn(Material.PLAYER_HEAD,
                    "&7" + String.valueOf(row.get("name")),
                    "&f" + val(row.get("bio")),
                    "&8" + val(row.get("note"))));
        }
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    private void openElections(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder("elections"), 54,
                Msg.color("&bВыборы"));
        int slot = 0;
        for (Map<String, Object> entry : plugin.politics().elections()) {
            if (slot >= 53) break;
            List<Component> lore = new ArrayList<>();
            long time = num(entry.get("time"));
            lore.add(Msg.color("&8" + (time > 0
                    ? java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(time),
                            java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                    : "—")));
            lore.add(Msg.color("&7голосовавших: &f" + num(entry.get("voters"))));
            Object p = entry.get("parties");
            if (p instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> row) {
                        lore.add(Msg.color("&d" + row.get("party")
                                + "&7: депутатов &f" + row.get("deputies")
                                + "&7, голосов &f" + row.get("percent") + "%"));
                    }
                }
            }
            inv.setItem(slot++, btnMulti(Material.BOOK, "&bВыборы #" + (slot + 1), lore));
        }
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        player.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        switch (holder.page()) {
            case "root" -> {
                switch (slot) {
                    case 11 -> openParties(player);
                    case 13 -> openTsars(player);
                    case 15 -> openElections(player);
                    case 22 -> player.closeInventory();
                    default -> { }
                }
            }
            case "parties", "tsars", "elections" -> {
                if (slot == 53) openRoot(player);
            }
            default -> { }
        }
    }

    private static String val(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long num(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o != null) {
            try { return Long.parseLong(String.valueOf(o).replaceAll("[^0-9-]", "")); }
            catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    private ItemStack btn(Material mat, String name, String... lore) {
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Msg.color(l));
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            meta.lore(lines);
        });
        return stack;
    }

    private ItemStack btnMulti(Material mat, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(mat);
        stack.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            meta.lore(lore);
        });
        return stack;
    }
}

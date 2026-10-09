package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.magic.Element;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Админ-панель (Shift+Q у OP под /quirks.admin):
 * хаб с головами игроков → редактор причуд (клик — цикл 0→1→2→3) и магии
 * (стихии, уровни, мана, сброс).
 */
public final class AdminMenu implements Listener {

    private final VoidQuirksPlugin plugin;

    public AdminMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private static VoidQuirksPlugin staticPlugin;

    public static void openHub(VoidQuirksPlugin plugin, Player admin) {
        staticPlugin = plugin;
        HubHolder holder = new HubHolder();
        Inventory inv = Bukkit.createInventory(holder, 54, Msg.color("&c&lАдмин: причуды & магия"));
        holder.inv = inv;
        int slot = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (slot >= 54) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(player);
                meta.displayName(Msg.color("&e" + player.getName()));
                List<Component> lore = new ArrayList<>();
                lore.add(Msg.color("&7Клик — редактировать"));
                PlayerData data = plugin.storage().get(player.getUniqueId());
                if (data != null) {
                    StringBuilder quirks = new StringBuilder();
                    for (var entry : data.entries()) {
                        if (quirks.length() > 0) quirks.append(", ");
                        quirks.append(entry.getKey().display()).append(" ").append(entry.getValue());
                    }
                    lore.add(Msg.color("&7Причуды: &d" + (quirks.length() == 0 ? "—" : quirks.toString())));
                    Element element = Element.byId(data.magicElement());
                    lore.add(Msg.color("&7Магия: &b" + (element == null ? "—"
                            : element.display() + " ур." + data.magicLevel())));
                }
                meta.lore(lore);
            });
            inv.setItem(slot++, head);
        }
        if (slot == 0) {
            inv.setItem(22, button(Material.BARRIER, "&7Никого нет онлайн"));
        }
        admin.openInventory(inv);
    }

    // ---------- редактор игрока ----------

    private void openEditor(Player admin, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            Msg.send(admin, "target-offline");
            return;
        }
        EditorHolder holder = new EditorHolder(targetId);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Msg.color("&c" + target.getName() + " &8| причуды/магия"));
        holder.inv = inv;
        fillEditor(holder, target);
        admin.openInventory(inv);
    }

    private void fillEditor(EditorHolder holder, Player target) {
        Inventory inv = holder.inv;
        PlayerData data = plugin.magic().dataOrCreate(target);
        // причуды: слоты 10..17 (клик — цикл 0→1→2→3)
        Quirk[] quirks = {Quirk.AXE, Quirk.SPIDER, Quirk.BEDROCK, Quirk.SCULK,
            Quirk.FARMER, Quirk.CAT, Quirk.AMPHIBIAN, Quirk.ENGINEER};
        Material[] icons = {Material.DIAMOND_AXE, Material.COBWEB, Material.BEDROCK, Material.SCULK,
            Material.DIAMOND_HOE, Material.COD, Material.TRIDENT, Material.REDSTONE};
        for (int i = 0; i < quirks.length; i++) {
            Quirk quirk = quirks[i];
            int level = data.levelOf(quirk);
            ItemStack icon = button(icons[i], (level > 0 ? "&d" : "&8") + quirk.display()
                    + (level > 0 ? " &7ур. " + level : " &7—"));
            lore(icon, "&7Клик — сменить уровень (0→1→2→3)");
            int slot = 10 + i;
            inv.setItem(slot, icon);
            holder.quirkSlots[slot] = quirk;
        }
        // магия
        Element element = Element.byId(data.magicElement());
        inv.setItem(28, button(Material.ENCHANTED_BOOK,
                "&bСтихия: " + (element == null ? "&7—" : "&d" + element.display())));
        loreOf(inv.getItem(28), "&7Клик — сменить стихию (кругом)");
        inv.setItem(30, button(Material.GOLD_NUGGET, "&aУровень: &d" + data.magicLevel() + " &7(+1)"));
        inv.setItem(31, button(Material.IRON_NUGGET, "&cУровень: &d" + data.magicLevel() + " &7(−1)"));
        inv.setItem(32, button(Material.LAPIS_LAZULI, "&bМана: &fполная"));
        inv.setItem(34, button(Material.RED_STAINED_GLASS, "&cСтереть магию"));
        loreOf(inv.getItem(28), "");
        inv.setItem(45, button(Material.ARROW, "&7← назад к игрокам"));
    }

    private static void lore(ItemStack item, String text) {
        item.editMeta(meta -> meta.lore(List.of(Msg.color(text))));
    }

    private static void loreOf(ItemStack item, String text) {
        if (item != null && !text.isEmpty()) lore(item, text);
    }

    private static ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Msg.color(name)));
        return item;
    }

    // ---------- события ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof HubHolder) && !(holder instanceof EditorHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        if (holder instanceof HubHolder) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            if (!(clicked.getItemMeta() instanceof SkullMeta skull) || skull.getOwnerProfile() == null) return;
            UUID targetId = skull.getOwnerProfile().getUniqueId();
            openEditor(admin, targetId);
            return;
        }

        EditorHolder editor = (EditorHolder) holder;
        Player target = Bukkit.getPlayer(editor.targetId);
        if (target == null) return;
        int slot = event.getRawSlot();
        PlayerData data = plugin.magic().dataOrCreate(target);

        if (slot == 45) {
            openHub(plugin, admin);
            return;
        }
        Quirk quirk = editor.quirkSlots[slot];
        if (quirk != null) {
            int level = data.levelOf(quirk);
            int next = (level + 1) % 4;
            if (next == 0) {
                plugin.quirks().remove(target, quirk);
            } else {
                plugin.quirks().assign(target, quirk, next);
            }
            fillEditor(editor, target);
            return;
        }
        switch (slot) {
            case 28 -> { // стихии кругом: none → fire → … → light → none
                Element current = Element.byId(data.magicElement());
                Element[] order = Element.values();
                if (current == null) {
                    data.magicElement(order[0].id());
                } else {
                    int idx = current.ordinal() + 1;
                    data.magicElement(idx >= order.length ? null : order[idx].id());
                }
            }
            case 30 -> data.magicLevel(data.magicLevel() + 1);
            case 31 -> data.magicLevel(Math.max(1, data.magicLevel() - 1));
            case 32 -> plugin.magic().refillMana(target);
            case 34 -> plugin.magic().resetMagic(target);
            default -> { return; }
        }
        plugin.storage().save();
        fillEditor(editor, target);
    }

    private static final class HubHolder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final class EditorHolder implements InventoryHolder {

        final UUID targetId;
        final Quirk[] quirkSlots = new Quirk[54];
        Inventory inv;

        EditorHolder(UUID targetId) {
            this.targetId = targetId;
        }

        @Override
        public Inventory getInventory() { return inv; }
    }
}

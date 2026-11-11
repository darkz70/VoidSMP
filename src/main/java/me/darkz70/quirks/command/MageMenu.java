package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.magic.Element;
import me.darkz70.quirks.magic.Spell;
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

/**
 * Причуда «Маг» (/mage): удобное меню прямого выбора стихии — без крафта
 * фолианта и без ограничения в 4 книги для тьмы/света. Полностью ОТДЕЛЬНО
 * от старой системы: фолианты стихий крафтятся и работают для всех игроков
 * как раньше, причуда «Маг» не требуется и ничего не блокирует без себя.
 * Сюда же переехал список всех нынешних заклинаний (превью в лоре) —
 * новые заклинания будут добавляться сюда по мере появления.
 */
public final class MageMenu implements Listener {

    private final VoidQuirksPlugin plugin;

    public MageMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private record Holder() implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    public boolean hasAccess(Player player) {
        return plugin.quirks().levelOf(player, Quirk.MAGE) > 0
                || plugin.quirks().levelOf(player, Quirk.ADMIN) > 0;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Msg.color("&5&lВыбор магии &8(причуда «Маг»)"));
        Element current = plugin.magic().elementOf(player);
        int[] slots = {10, 11, 12, 14, 15, 16};
        Element[] elements = Element.values();
        for (int i = 0; i < elements.length; i++) {
            inv.setItem(slots[i], elementButton(elements[i], elements[i] == current));
        }
        player.openInventory(inv);
    }

    private ItemStack elementButton(Element element, boolean selected) {
        ItemStack item = new ItemStack(element.catalyst());
        List<Component> lore = new ArrayList<>();
        lore.add(Msg.color(selected ? "&a&lТекущая стихия" : "&7Клик — выбрать эту стихию"));
        lore.add(Msg.color("&7Сбрасывает уровень магии до 1, мана восполнится."));
        lore.add(Component.empty());
        lore.add(Msg.color("&fЗаклинания:"));
        Spell[] spells = Spell.of(element);
        for (Spell spell : spells) {
            lore.add(Msg.color("&7 • &f" + spell.name() + " &8(ур. " + spell.requiredLevel() + ")"));
        }
        item.editMeta(meta -> {
            meta.displayName(Msg.color((selected ? "&a&l" : "&d&l") + element.emoji() + " " + element.display()));
            meta.lore(lore);
        });
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!hasAccess(player)) {
            player.closeInventory();
            return;
        }
        Element element = switch (event.getRawSlot()) {
            case 10 -> Element.values()[0];
            case 11 -> Element.values()[1];
            case 12 -> Element.values()[2];
            case 14 -> Element.values()[3];
            case 15 -> Element.values()[4];
            case 16 -> Element.values()[5];
            default -> null;
        };
        if (element == null) return;
        plugin.magic().pickElementDirect(player, element);
        player.closeInventory();
    }
}

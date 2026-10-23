package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mob.ZayceKrol;
import me.darkz70.quirks.mob.ZayceKrol.Role;
import me.darkz70.quirks.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Админ (OP) ПКМ по зайцекролу → меню управления: поменять нужное имя и партию
 * (у депутата партия отображается в имени). Изменения помечаются PDC-метками,
 * ежесекундные переназывания таски их уважают.
 */
public final class ZayceMobMenu implements Listener {

    private final VoidQuirksPlugin plugin;
    private final NamespacedKey partyKey;
    private final NamespacedKey nameKey;

    private final Map<UUID, UUID> renamePending = new HashMap<>(); // админ -> зайцекрол

    public ZayceMobMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        this.partyKey = new NamespacedKey(plugin, "zayce.party");
        this.nameKey = new NamespacedKey(plugin, "zayce.name");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public NamespacedKey partyKey() {
        return partyKey;
    }

    public NamespacedKey nameKey() {
        return nameKey;
    }

    private record ZHolder(String page, UUID mob) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 27, Component.empty());
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Rabbit rabbit)) return;
        if (!ZayceKrol.isZayce(rabbit)) return;
        Player admin = event.getPlayer();
        if (!admin.isOp() && !admin.hasPermission("quirks.admin")) return;
        event.setCancelled(true);
        openRoot(admin, rabbit);
    }

    private void openRoot(Player admin, Rabbit rabbit) {
        Role role = ZayceKrol.roleOf(rabbit);
        Inventory inv = Bukkit.createInventory(new ZHolder("root", rabbit.getUniqueId()), 27,
                Msg.color("&eЗайцекрол: " + roleDisplay(role)));
        inv.setItem(12, btn(Material.WRITABLE_BOOK, "&fПоменять имя",
                "&7Сейчас: &f" + currentName(rabbit), "&7Клик — ввести в чате"));
        inv.setItem(14, btn(Material.WHITE_BANNER, "&dВыбрать партию",
                "&7Сейчас: &f" + currentParty(rabbit),
                role == Role.DEPUTY ? "&7Клик — список партий" : "&8Полноценно отображается у депутатов"));
        inv.setItem(22, btn(Material.ARROW, "&7Назад"));
        admin.openInventory(inv);
    }

    private void openPartyPage(Player admin, Rabbit rabbit) {
        Inventory inv = Bukkit.createInventory(new ZHolder("party", rabbit.getUniqueId()), 54,
                Msg.color("&dПартия зайцекрола"));
        int slot = 0;
        for (String name : plugin.politics().parties().keySet()) {
            if (slot >= 53) break;
            inv.setItem(slot++, btn(Material.WHITE_BANNER, "&a" + name, "&7Клик — назначить"));
        }
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        admin.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ZHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        var mob = admin.getWorld().getEntity(holder.mob());
        if (!(mob instanceof Rabbit rabbit)) { admin.closeInventory(); return; }
        switch (holder.page()) {
            case "root" -> {
                if (event.getRawSlot() == 12) {
                    renamePending.put(admin.getUniqueId(), rabbit.getUniqueId());
                    admin.closeInventory();
                    admin.sendMessage(Msg.color("&aВведи в чате нужное имя зайцекрола (одна строка):"));
                } else if (event.getRawSlot() == 14) {
                    openPartyPage(admin, rabbit);
                } else if (event.getRawSlot() == 22) {
                    admin.closeInventory();
                }
            }
            case "party" -> {
                if (event.getRawSlot() == 53) { openRoot(admin, rabbit); return; }
                int i = 0;
                String picked = null;
                for (String name : plugin.politics().parties().keySet()) {
                    if (i++ == event.getRawSlot()) { picked = name; break; }
                }
                if (picked != null) {
                    rabbit.getPersistentDataContainer().set(partyKey, PersistentDataType.STRING, picked);
                    refreshName(rabbit);
                    admin.sendMessage(Msg.color("&aПартия: &f" + picked));
                }
                openRoot(admin, rabbit);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player admin = event.getPlayer();
        UUID mobId = renamePending.remove(admin.getUniqueId());
        if (mobId == null) return;
        event.setCancelled(true);
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message());
        Bukkit.getScheduler().runTask(plugin, () -> {
            var entity = admin.getWorld().getEntity(mobId);
            if (entity instanceof Rabbit rabbit) {
                rabbit.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, text);
                refreshName(rabbit);
                admin.sendMessage(Msg.color("&aИмя: &f" + text));
            }
        });
    }

    // ---------- отображаемое имя моба ----------

    private static String roleDisplay(Role role) {
        return switch (role) {
            case FARMER -> "Фермер";
            case GUARD -> "Охранник";
            case ELITE -> "Элитный охранник";
            case RESIDENT -> "Житель";
            case TSAR -> "Царь";
            case DEPUTY -> "Депутат";
            case SERVANT -> "Слуга";
            case REVOLUTIONER -> "Революционер";
        };
    }

    private String currentName(Rabbit rabbit) {
        String name = rabbit.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        return name == null ? "(стандартное)" : name;
    }

    private String currentParty(Rabbit rabbit) {
        String party = rabbit.getPersistentDataContainer().get(partyKey, PersistentDataType.STRING);
        if (party != null) return party;
        return plugin.getConfig().getString("zaycekrol.deputy-party", "Без партии");
    }

    /** Имя моба = личное имя (если задано) · роль · (партия) — у депутата по полной. */
    public void refreshName(Rabbit rabbit) {
        String name = rabbit.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
        String party = rabbit.getPersistentDataContainer().get(partyKey, PersistentDataType.STRING);
        if (party == null) party = plugin.getConfig().getString("zaycekrol.deputy-party", "Без партии");
        Role role = ZayceKrol.roleOf(rabbit);
        switch (role) {
            case DEPUTY -> rabbit.customName(Msg.color(
                    (name == null ? "&eЗайцекрол" : "&e" + name) + " &7· &fДепутат &8(&d" + party + "&8)"));
            default -> rabbit.customName(Msg.color(
                    (name == null ? "&eЗайцекрол" : "&e" + name) + " &7· &f" + roleDisplay(role)));
        }
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
}

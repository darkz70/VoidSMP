package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.mob.ZayceKrol;
import me.darkz70.quirks.util.Msg;
import me.darkz70.quirks.util.Perms;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Shift+ПКМ по ЛЮБОМУ зайцекролу (OP/quirks.admin) — меню КОМАНД: назначить
 * конкретного игрока (или нескольких) целью атаки — ОТДЕЛЬНО для революционеров
 * и отдельно для царских войск (охрана + элита). Команда действует на ВСЮ
 * фракцию сразу (не только на моба, по которому кликнули), в том числе на
 * ещё не заспавненных бойцов, и остаётся в силе, пока её не сбросят.
 * <p>
 * Не путать с {@link ZayceMobMenu} — тот открывается обычным (без shift) ПКМ
 * и меняет имя/партию одного конкретного моба.
 */
public final class ZayceCommandMenu implements Listener {

    private final VoidQuirksPlugin plugin;

    private record Holder(String page) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return Bukkit.createInventory(this, 54, Component.empty());
        }
    }

    public ZayceCommandMenu(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Rabbit rabbit)) return;
        if (!ZayceKrol.isZayce(rabbit)) return;
        Player admin = event.getPlayer();
        if (!Perms.isAdmin(admin)) return;
        if (!admin.isSneaking()) return;
        event.setCancelled(true);
        openRoot(admin);
    }

    private void openRoot(Player admin) {
        Inventory inv = Bukkit.createInventory(new Holder("root"), 27,
                Msg.color("&eКоманды зайцекролов"));
        inv.setItem(11, btn(Material.ZOMBIE_HEAD, "&cРеволюционеры",
                "&7Назначить цель(и) атаки для революционеров",
                targetsLine(plugin.zayceTask().revolutionerTargets())));
        inv.setItem(15, btn(Material.GOLDEN_HELMET, "&bЦарские войска",
                "&7Назначить цель(и) атаки для охраны и элиты",
                targetsLine(plugin.zayceTask().loyalistTargets())));
        inv.setItem(22, btn(Material.ARROW, "&7Закрыть"));
        admin.openInventory(inv);
    }

    private String targetsLine(Set<UUID> targets) {
        if (targets.isEmpty()) return "&8Сейчас целей нет";
        StringBuilder names = new StringBuilder();
        for (UUID id : targets) {
            Player p = Bukkit.getPlayer(id);
            if (names.length() > 0) names.append(", ");
            names.append(p != null ? p.getName() : "офлайн");
        }
        return "&8Цели: &f" + names;
    }

    private void openFaction(Player admin, String faction) {
        Set<UUID> targets = targetsOf(faction);
        Inventory inv = Bukkit.createInventory(new Holder("faction:" + faction), 54,
                Msg.color(faction.equals("rev") ? "&cЦели революционеров" : "&bЦели царских войск"));
        int slot = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slot >= 52) break;
            boolean picked = targets.contains(online.getUniqueId());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            head.editMeta(SkullMeta.class, meta -> {
                meta.setOwningPlayer(online);
                meta.displayName(Msg.color((picked ? "&a✔ " : "&7") + online.getName()));
                List<Component> lore = new ArrayList<>();
                lore.add(Msg.color(picked ? "&aВ списке целей — клик, чтобы убрать"
                        : "&7Клик — добавить в цели"));
                meta.lore(lore);
            });
            inv.setItem(slot++, head);
        }
        inv.setItem(52, btn(Material.BARRIER, "&cОчистить все цели"));
        inv.setItem(53, btn(Material.ARROW, "&7Назад"));
        admin.openInventory(inv);
    }

    private Set<UUID> targetsOf(String faction) {
        return faction.equals("rev") ? plugin.zayceTask().revolutionerTargets()
                : plugin.zayceTask().loyalistTargets();
    }

    @EventHandler(ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        if (holder.page().equals("root")) {
            switch (event.getRawSlot()) {
                case 11 -> openFaction(admin, "rev");
                case 15 -> openFaction(admin, "loy");
                case 22 -> admin.closeInventory();
                default -> { }
            }
            return;
        }

        if (!holder.page().startsWith("faction:")) return;
        String faction = holder.page().substring("faction:".length());
        Set<UUID> targets = targetsOf(faction);
        int slot = event.getRawSlot();
        if (slot == 53) {
            openRoot(admin);
            return;
        }
        if (slot == 52) {
            targets.clear();
            admin.sendMessage(Msg.color("&aЦели очищены."));
            openFaction(admin, faction);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta skull) || skull.getOwnerProfile() == null) return;
        UUID targetId = skull.getOwnerProfile().getUniqueId();
        boolean added = targets.add(targetId);
        if (!added) targets.remove(targetId);
        Player target = Bukkit.getPlayer(targetId);
        String name = target != null ? target.getName() : targetId.toString();
        admin.sendMessage(Msg.color(added
                ? "&a" + name + " добавлен(а) в цели."
                : "&7" + name + " убран(а) из целей."));
        openFaction(admin, faction);
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

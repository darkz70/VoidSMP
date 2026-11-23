package me.darkz70.quirks.arcana;

import java.util.List;
import me.darkz70.quirks.PlayerData;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Клавиши и каст НОВОЙ магии Arcana (#99, причуда MAGE — обычным игрокам): ПКМ
 * посохом — каст, Q/F — цикл заклинаний, Shift+F — меню, Shift+Q — смена активной
 * стихии. Отдельно и независимо от старого {@link me.darkz70.quirks.magic.MagicListener}
 * (теперь на технической причуде ADMIN_MAG, разные PDC-метки предметов, поэтому
 * события не пересекаются по содержимому руки; единственная точка стыковки —
 * Shift+Q, которая у старого слушателя при OP открывает админ-меню: здесь мы
 * перехватываем событие РАНЬШЕ и гасим его, если в руке именно посох Арканы
 * и у игрока есть причуда MAGE — тогда приоритет у смены стихии).
 */
public final class ArcanaListener implements Listener {

    private final VoidQuirksPlugin plugin;

    public ArcanaListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    private ArcanaSystem arcana() {
        return plugin.arcana();
    }

    // ---------- ПКМ — каст / изучение фолианта ----------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        if (!right || item == null) return;

        Arcana tome = ArcanaItems.tomeElement(item);
        if (tome != null) {
            event.setCancelled(true);
            if (arcana().learnElement(player, tome)) {
                item.setAmount(item.getAmount() - 1);
            }
            return;
        }

        if (!ArcanaItems.isFocusStaff(item)) return;
        if (!arcana().hasQuirk(player)) {
            Msg.send(player, "arcana-no-quirk");
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked != null && clicked.getType().isInteractable()
                && clicked.getType() != Material.ENCHANTING_TABLE && !player.isSneaking()) {
            return;
        }
        event.setCancelled(true);
        arcana().cast(player);
    }

    // ---------- Q / Shift+Q — цикл заклинаний / смена стихии ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (!ArcanaItems.isFocusStaff(item)) return;
        Player player = event.getPlayer();
        if (!arcana().hasQuirk(player)) return;
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            return;
        }
        event.setCancelled(true);
        if (player.isSneaking()) {
            arcana().cycleElement(player);
        } else {
            arcana().cycleSpell(player);
        }
    }

    // ---------- F / Shift+F — цикл / меню заклинаний ----------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        boolean focus = ArcanaItems.isFocusStaff(event.getMainHandItem()) || ArcanaItems.isFocusStaff(event.getOffHandItem());
        if (!focus) return;
        Player player = event.getPlayer();
        if (!arcana().hasQuirk(player)) return;
        event.setCancelled(true);
        Arcana element = arcana().activeElement(player);
        if (element == null) {
            Msg.send(player, "arcana-no-element");
            return;
        }
        if (player.isSneaking()) {
            openMenu(player, element);
        } else {
            arcana().cycleSpell(player);
        }
    }

    // ---------- меню заклинаний (27 слотов) ----------

    public void openMenu(Player player, Arcana element) {
        SpellMenu menu = new SpellMenu(element);
        Inventory inv = Bukkit.createInventory(menu, 27,
                Msg.comp("arcana-menu-title", "%element%", element.display(), "%emoji%", element.emoji()));
        menu.inv = inv;
        fill(menu);
        player.openInventory(inv);
    }

    private void fill(SpellMenu menu) {
        Inventory inv = menu.inv;
        Player player = menu.player();
        if (player == null) return;
        PlayerData data = arcana().data(player);
        ArcanaSpell[] spells = spellsOf(menu.element);
        int level = data == null ? 0 : data.arcanaLevelOf(menu.element.id());
        for (int i = 0; i < spells.length; i++) {
            ArcanaSpell spell = spells[i];
            final int fi = i;
            int slot = 9 + i;
            ItemStack item = new ItemStack(menu.element.icon());
            item.editMeta(meta -> {
                boolean unlocked = level >= spell.requiredLevel();
                boolean selected = data != null && data.arcanaSpellIndex(menu.element.id()) == fi;
                meta.displayName(Msg.color((unlocked ? (selected ? "&a✔ " : "&f") : "&8✘ ") + spell.name()));
                meta.lore(List.of(
                        Msg.color("&7Мана: &b" + (int) spell.mana() + " &8| &7КД: &b" + (int) spell.cooldownSeconds() + "с"),
                        Msg.color("&7Нужен уровень: &b" + spell.requiredLevel()),
                        Msg.color(unlocked ? (selected ? "&aСейчас выбрано" : "&eКлик — выбрать")
                                : "&8Закрыто до " + spell.requiredLevel() + " ур.")));
            });
            inv.setItem(slot, item);
        }
    }

    private static ArcanaSpell[] spellsOf(Arcana element) {
        return ArcanaSpells.of(element);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpellMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        ArcanaSpell[] spells = spellsOf(menu.element);
        int idx = slot - 9;
        if (idx < 0 || idx >= spells.length) return;
        PlayerData data = arcana().data(player);
        if (data == null) return;
        Arcana current = arcana().activeElement(player);
        if (current != menu.element) return;
        int level = data.arcanaLevelOf(menu.element.id());
        if (level < spells[idx].requiredLevel()) {
            Msg.send(player, "arcana-spell-locked",
                    "%level%", String.valueOf(spells[idx].requiredLevel()), "%spell%", spells[idx].name());
            return;
        }
        data.arcanaSpellIndex(menu.element.id(), idx);
        plugin.storage().save();
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        fill(menu);
        arcana().showBar(player, menu.element, data, idx);
    }

    /** Holder меню заклинаний Арканы. */
    static final class SpellMenu implements InventoryHolder {

        final Arcana element;
        Inventory inv;

        SpellMenu(Arcana element) {
            this.element = element;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }

        @Nullable
        Player player() {
            return inv == null ? null : inv.getViewers().stream()
                    .filter(v -> v instanceof Player).map(v -> (Player) v).findFirst().orElse(null);
        }
    }
}

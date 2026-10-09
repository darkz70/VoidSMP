package me.darkz70.quirks.command;

import java.util.ArrayList;
import java.util.List;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.listener.CraftListener;
import me.darkz70.quirks.magic.Element;
import me.darkz70.quirks.mechanic.BrewTree;
import me.darkz70.quirks.mechanic.SoulShards;
import me.darkz70.quirks.Quirk;
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
 * Меню гайда (/gaid, только OP):
 * корень → «Описания» (тематические книги: открываются читалкой) или «Предметы»
 * (все предметы плагина → страница крафта + кнопки «Взять»/«Назад»).
 */
public final class GaidMenus implements Listener {

    private final VoidQuirksPlugin plugin;

    public GaidMenus(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- корень ----------

    public void openRoot(Player player) {
        RootHolder holder = new RootHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.color("&5&lГайд VoidSMP"));
        holder.inv = inv;
        inv.setItem(11, button(Material.BOOK, "&dОписания", "&7Механики и команды — тематические книги"));
        inv.setItem(15, button(Material.CHEST, "&eПредметы", "&7Все предметы плагина + крафты (+взять себе)"));
        player.openInventory(inv);
    }

    // ---------- описания (меню книг) ----------

    private void openBooksMenu(Player player) {
        BooksHolder holder = new BooksHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.color("&5Гайд: описания"));
        holder.inv = inv;
        inv.setItem(10, button(Material.WRITABLE_BOOK, "&dКнига причуд", "&7Восемь причуд, души, бинды"));
        inv.setItem(12, button(Material.WRITABLE_BOOK, "&bКнига магии", "&7Стихии, мана, заклинания"));
        inv.setItem(14, button(Material.WRITABLE_BOOK, "&aКнига зелий", "&7Дерево варки, секреты, пределы"));
        inv.setItem(16, button(Material.WRITABLE_BOOK, "&6Книга команд", "&7/quirk, /magic, /gaid, админки"));
        inv.setItem(22, button(Material.ARROW, "&7← назад"));
        player.openInventory(inv);
    }

    // ---------- предметы (каталог) ----------

    private void openItemsMenu(Player player) {
        ItemsHolder holder = new ItemsHolder(itemsList());
        Inventory inv = Bukkit.createInventory(holder, 54, Msg.color("&5Гайд: предметы"));
        holder.inv = inv;
        int slot = 0;
        for (ItemStack item : holder.items) {
            if (slot >= 45) break;
            inv.setItem(slot++, item);
        }
        inv.setItem(49, button(Material.ARROW, "&7← назад"));
        player.openInventory(inv);
    }

    /** Все предметы плагина для каталога. */
    private List<ItemStack> itemsList() {
        List<ItemStack> items = new ArrayList<>();
        // книги
        for (Element element : Element.values()) items.add(CraftListener.makeElementBook(element));
        for (int tier = 1; tier <= 5; tier++) items.add(CraftListener.makeUpgradeBook(tier));
        items.add(BrewTree.makeFocusCrystal());
        // зелья по тегам
        for (String tag : brewTags()) {
            ItemStack item = BrewTree.byTag(tag, null);
            if (item != null) items.add(item);
        }
        // прочее
        items.add(CraftListener.makeFertilizer());
        items.add(SoulShards.makeShard(Quirk.AXE, 1));
        return items;
    }

    private static String[] brewTags() {
        return new String[]{"neutral", "antidote", "infect", "samogon", "deny", "confirm", "disable",
            "quirkall", "life", "paces", "noharm", "norestore", "flypot", "god", "berserk", "basis",
            "lucky", "unlucky", "grandsam", "return", "infusion", "retrain", "satpot", "knowledge",
            "mind", "miner", "dull", "demagic",
            // v1.1
            "doublepoison", "nature", "druid", "naturepoison", "warrior", "gladiator", "plague",
            "epidemic", "bastion", "fortress", "weightless", "angel", "witherpot", "necro",
            "darkpotion", "lightpotion", "blindpotion", "nightmare", "madness", "manapot", "archimage",
            "greatarch", "darkmagic", "lightmagic", "mushroomspirit", "forestfeast", "newlife",
            "boiledegg", "chick", "depths", "panda", "oceanid", "albatross", "nest"};
    }

    // ---------- страница предмета ----------

    private void openItemPage(Player player, int index) {
        List<ItemStack> items = itemsList();
        if (index < 0 || index >= items.size()) return;
        ItemStack item = items.get(index);
        ItemPageHolder holder = new ItemPageHolder(index);
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.color("&5Предмет"));
        holder.inv = inv;
        inv.setItem(4, item);
        inv.setItem(13, button(Material.CRAFTING_TABLE, "&eКрафт", recipeText(item)));
        inv.setItem(15, button(Material.GREEN_STAINED_GLASS, "&aВзять себе"));
        inv.setItem(18, button(Material.ARROW, "&7← назад к предметам"));
        player.openInventory(inv);
    }

    /** Текст крафта по предмету (инвентарная помощь для админа). */
    private String[] recipeText(ItemStack item) {
        String tag = BrewTree.markOf(item);
        if (tag == null && SoulShards.isShard(item)) {
            return new String[]{"&7Освобождение душ в лабиринте или /quirk item"};
        }
        if (tag == null) return new String[]{"&7(нет)"};
        if (tag.startsWith("elem-book-")) {
            Element element = Element.byId(tag.substring(10));
            if (element == Element.LIGHT || element == Element.DARK) {
                return new String[]{"&7×: варёное яйцо + птенца + глубин + панда + океанид",
                    "&7+ альбатрос + воздушное гнездо (вся ветка, 7 предметов)",
                    "&8(оба фолианта выдаются парой)"};
            }
            return new String[]{"&7Верстак: книга + 4 аметиста + " + (element == null ? "?" : element.emoji())};
        }
        if (tag.startsWith("upbook-")) {
            return new String[]{"&7Верстак: ветка медных→алмазных книг", "&8(только маги ур. 1+)"};
        }
        return switch (tag) {
            case "neutral" -> new String[]{"&6×&8: вред + исцеление + вред + исцеление + редстоун + порох",
                "&8(только маги ур. 1+)"};
            case "antidote" -> new String[]{"&8×: нейтралка + смола + сота"};
            case "infect" -> new String[]{"&8×: нейтралка + скалк-сенсор"};
            case "samogon" -> new String[]{"&8×: нейтралка + мёд + ягоды (ап: снова +мёд+ягоды)"};
            case "deny" -> new String[]{"&8×: 5 нейтралок"};
            case "confirm" -> new String[]{"&8×: 6 нейтралок"};
            case "disable" -> new String[]{"&8×: нейтралка + топор + мотыга + паутина + скалк + рыба",
                "&8+ изумруд-блок + редстоун-блок + трезубец"};
            case "quirkall" -> new String[]{"&8×: отключение + подтверждение"};
            case "life" -> new String[]{"&8×: нейтралка + исцеление + регенерация"};
            case "paces" -> new String[]{"&8×: нейтралка + скорость + прыгучесть"};
            case "noharm" -> new String[]{"&8×: нейтралка + вред"};
            case "norestore" -> new String[]{"&8×: нейтралка + исцеление"};
            case "flypot" -> new String[]{"&8×: нейтралка + 2×плав.падения + нейтралка"};
            case "god" -> new String[]{"&8×: нейтралка + полёт + ускорение + жизнь", "&8+ ник.вреда + ник.восст + причуды"};
            case "berserk" -> new String[]{"&8×: нейтралка + сила + жизнь + самогон"};
            case "basis" -> new String[]{"&8×: 2 нейтралки + исцеление + вред", "&8(только маги ур. 1+)"};
            case "lucky" -> new String[]{"&8×: нейтралка + основа"};
            case "unlucky" -> new String[]{"&8×: удача + нейтралка"};
            case "grandsam" -> new String[]{"&8×: никакой удачи + самогон"};
            case "return" -> new String[]{"&8×: нейтралка + 8 зелий божества"};
            case "infusion" -> new String[]{"&8≡ варка: вода + любой гриб"};
            case "retrain" -> new String[]{"&8×: настойка + нейтралка + книга"};
            case "satpot" -> new String[]{"&8×: 2 настойки"};
            case "knowledge" -> new String[]{"&8≡ варка: вода + книга"};
            case "mind" -> new String[]{"&8×: 2 знания"};
            case "miner" -> new String[]{"&8×: знания + нейтралка"};
            case "dull" -> new String[]{"&8×: ум + отрицание"};
            case "demagic" -> new String[]{"&8×: потупление + нейтралка + любое зелье + ум + 5 алмаз-блоков"};
            case "doublepoison" -> new String[]{"&8×: нейтралка + отравление + нейтралка"};
            case "nature" -> new String[]{"&8×: нейтралка + 7 саженцев + костная мука"};
            case "druid" -> new String[]{"&8×: природы + исцеление"};
            case "naturepoison" -> new String[]{"&8×: природы + настойка гриба"};
            case "warrior" -> new String[]{"&8×: нейтралка + сила + скорость + железный меч"};
            case "gladiator" -> new String[]{"&8×: воина + берсерка"};
            case "plague" -> new String[]{"&8×: нейтралка + отравление + вред + 4 паучьих глаза"};
            case "epidemic" -> new String[]{"&8×: чумы + двойного отравления"};
            case "bastion" -> new String[]{"&8×: нейтралка + регенерация + щит"};
            case "fortress" -> new String[]{"&8×: бастиона + жизни"};
            case "weightless" -> new String[]{"&8×: нейтралка + плавное падение + 4 пера"};
            case "angel" -> new String[]{"&8×: невесомости + полёта"};
            case "witherpot" -> new String[]{"&8×: нейтралка + череп иссушителя + вред"};
            case "necro" -> new String[]{"&8×: иссушения + тьмы + 7 костей"};
            case "darkpotion" -> new String[]{"&8×: нейтралка + скалк + светочернила"};
            case "lightpotion" -> new String[]{"&8×: нейтралка + светоягоды + факел"};
            case "blindpotion" -> new String[]{"&8×: нейтралка + мешок с чернилами"};
            case "nightmare" -> new String[]{"&8×: нейтралка + тьмы + слепоты + 4 паутины"};
            case "madness" -> new String[]{"&8×: кошмара + великого самогона"};
            case "manapot" -> new String[]{"&8×: нейтралка + 4 аметиста + знания"};
            case "archimage" -> new String[]{"&8×: маны + ума"};
            case "greatarch" -> new String[]{"&8×: архимага + основы + книга прокачки"};
            case "darkmagic" -> new String[]{"&8×: настойка + маны + тьмы + скалк"};
            case "lightmagic" -> new String[]{"&8×: настойка + маны + света + светокамень"};
            case "mushroomspirit" -> new String[]{"&8×: 3 настойки гриба"};
            case "forestfeast" -> new String[]{"&8×: настойка + насыщения + исцеление"};
            case "newlife" -> new String[]{"&8×: настойка + переквалификации"};
            case "boiledegg" -> new String[]{"&8≡ печь/коптильня/костёр: яйцо"};
            case "chick" -> new String[]{"&8×: нейтралка + варёное яйцо"};
            case "depths" -> new String[]{"&8×: нейтралка + ламинария"};
            case "panda" -> new String[]{"&8×: нейтралка + бамбук"};
            case "oceanid" -> new String[]{"&8×: глубин + панды"};
            case "albatross" -> new String[]{"&8×: птенца + глубин"};
            case "nest" -> new String[]{"&8×: птенца + плавного падения"};
            case "focus-crystal" -> new String[]{"&7Верстак: 4 аметиста + 4 алмаза + стекло"};
            case "fertilizer" -> new String[]{"&7Верстак: 2 костной муки (только Фермер/Админ)"};
            default -> new String[]{"&7×: ручное совпадение верстака (секрет)"};
        };
    }

    // ---------- helpers ----------

    private static ItemStack button(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(Msg.color(name));
            if (loreLines.length > 0) {
                List<Component> lore = new ArrayList<>();
                for (String line : loreLines) lore.add(Msg.color(line));
                meta.lore(lore);
            }
        });
        return item;
    }

    // ---------- события ----------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof RootHolder || holder instanceof BooksHolder
                || holder instanceof ItemsHolder || holder instanceof ItemPageHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (holder instanceof RootHolder) {
            if (slot == 11) openBooksMenu(player);
            else if (slot == 15) openItemsMenu(player);
        } else if (holder instanceof BooksHolder) {
            switch (slot) {
                case 10 -> GaidBooks.openQuirks(player);
                case 12 -> GaidBooks.openMagic(player);
                case 14 -> GaidBooks.openBrewing(player);
                case 16 -> GaidBooks.openCommands(player);
                case 22 -> openRoot(player);
                default -> { }
            }
        } else if (holder instanceof ItemsHolder itemsHolder) {
            if (slot == 49) {
                openRoot(player);
                return;
            }
            if (slot < 0 || slot >= itemsHolder.items.size()) return;
            ItemStack item = itemsHolder.items.get(slot);
            if (item == null || item.getType().isAir()) return;
            openItemPage(player, itemsHolder.items.indexOf(item));
        } else if (holder instanceof ItemPageHolder pageHolder) {
            if (slot == 18) {
                openItemsMenu(player);
            } else if (slot == 15) {
                ItemStack item = itemsList().get(pageHolder.index);
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                Msg.send(player, "gaid-given");
            }
        }
    }

    private static final class RootHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class BooksHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final class ItemsHolder implements InventoryHolder {

        final List<ItemStack> items;
        Inventory inv;

        ItemsHolder(List<ItemStack> items) { this.items = items; }

        @Override public Inventory getInventory() { return inv; }
    }

    private static final class ItemPageHolder implements InventoryHolder {

        final int index;
        Inventory inv;

        ItemPageHolder(int index) { this.index = index; }

        @Override public Inventory getInventory() { return inv; }
    }
}

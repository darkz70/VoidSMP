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
 * корень → «Описания» (4 тематические книги), «Предметы» (каталог со страницами),
 * «Админ-панель» (Shift+Q-дубль) и «Настройки КД» (масштаб кулдаунов).
 */
public final class GaidMenus implements Listener {

    private static final int PER_PAGE = 45;

    private final VoidQuirksPlugin plugin;

    public GaidMenus(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- корень ----------

    public void openRoot(Player player) {
        RootHolder holder = new RootHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.color("&5&lГайд VoidSMP"));
        holder.inv = inv;
        inv.setItem(10, button(Material.BOOK, "&dОписания", "&7Механики и команды — тематические книги"));
        inv.setItem(12, button(Material.CHEST, "&eПредметы", "&7Все предметы плагина + крафты (+взять себе)"));
        inv.setItem(14, button(Material.PLAYER_HEAD, "&cАдмин-панель", "&7Причуды и магии онлайн-игроков"));
        inv.setItem(16, button(Material.CLOCK, "&bНастройки КД", "&7Масштаб кулдаунов заклинаний/зелий/вещей"));
        player.openInventory(inv);
    }

    // ---------- описания (меню книг) ----------

    private void openBooksMenu(Player player) {
        BooksHolder holder = new BooksHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.color("&5Гайд: описания"));
        holder.inv = inv;
        inv.setItem(10, button(Material.WRITABLE_BOOK, "&dКнига причуд", "&7Причуды, души, техпричуды"));
        inv.setItem(12, button(Material.WRITABLE_BOOK, "&bКнига магии", "&7Стихии, мана, заклинания"));
        inv.setItem(14, button(Material.WRITABLE_BOOK, "&aКнига зелий", "&7Дерево варки, секреты, стрелы"));
        inv.setItem(16, button(Material.WRITABLE_BOOK, "&6Книга команд", "&7/quirk, /magic, /gaid"));
        inv.setItem(22, button(Material.ARROW, "&7← назад"));
        player.openInventory(inv);
    }

    // ---------- предметы (каталог со страницами) ----------

    private void openItemsMenu(Player player, int page) {
        List<ItemStack> items = itemsList();
        int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        ItemsHolder holder = new ItemsHolder(items, page, pages);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Msg.color("&5Гайд: предметы &8(&e" + (page + 1) + "&7/&e" + pages + "&8)"));
        holder.inv = inv;
        int start = page * PER_PAGE;
        for (int slot = 0; slot < PER_PAGE && start + slot < items.size(); slot++) {
            inv.setItem(slot, items.get(start + slot));
        }
        if (page > 0) inv.setItem(45, button(Material.ARROW, "&7← страница " + page));
        inv.setItem(49, button(Material.BARRIER, "&7← в корень"));
        if (page < pages - 1) inv.setItem(53, button(Material.ARROW, "&7страница " + (page + 2) + " →"));
        player.openInventory(inv);
    }

    /** Все предметы плагина для каталога (зелья + их стрелы + книги + прочее). */
    private List<ItemStack> itemsList() {
        List<ItemStack> items = new ArrayList<>();
        // книги
        for (Element element : Element.values()) items.add(CraftListener.makeElementBook(element));
        for (int tier = 1; tier <= 5; tier++) items.add(CraftListener.makeUpgradeBook(tier));
        items.add(BrewTree.makeFocusCrystal());
        // зелья и их стрелы
        for (String tag : brewTags()) {
            ItemStack brew = BrewTree.byTag(tag, null);
            if (brew == null) continue;
            items.add(brew);
            ItemStack arrows = BrewTree.makeBrewArrows(brew);
            arrows.setAmount(32);
            items.add(arrows);
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
        if (tag == null && item.getType() == Material.TIPPED_ARROW) {
            tag = markOfArrow(item);
        }
        if (tag == null && SoulShards.isShard(item)) {
            return new String[]{"&7Освобождение душ в лабиринте или /quirk item"};
        }
        if (tag == null) return new String[]{"&7(нет)"};
        if (tag.startsWith("arrow:")) {
            return new String[]{"&7Варёное зелье (см. его страницу) + 8 обычных стрел = 32 таких"};
        }
        if (tag.startsWith("elem-book-")) {
            Element element = Element.byId(tag.substring(10));
            if (element == Element.LIGHT || element == Element.DARK) {
                return new String[]{"&7×: зелье океанида + зелье альбатроса + воздушного гнезда",
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
            case "disable" -> new String[]{"&8×: нейтралка + алм.топор + мотыга + паутина + скалк + рыба",
                "&8+ изумруд-блок + редстоун-блок + трезубец"};
            case "quirkall" -> new String[]{"&8×: зелье от причуды + подтверждение"};
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

    /** Тег, привязанный к наконечной стреле («arrow:<tag>» в brewTarget). */
    private static String markOfArrow(ItemStack item) {
        String raw = item.getPersistentDataContainer().get(
                me.darkz70.quirks.Keys.brewTarget, org.bukkit.persistence.PersistentDataType.STRING);
        return raw != null && raw.startsWith("arrow:") ? raw : null;
    }

    // ---------- настройки кулдаунов ----------

    private static final double[] SCALE_STEPS = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0};

    private void openCooldownsMenu(Player player) {
        CooldownsHolder holder = new CooldownsHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Msg.color("&5Настройки КД"));
        holder.inv = inv;
        fillCooldowns(inv);
        inv.setItem(22, button(Material.ARROW, "&7← в корень"));
        player.openInventory(inv);
    }

    private void fillCooldowns(Inventory inv) {
        String[] groups = {"spells", "potions", "items"};
        String[] names = {"&eЗаклинания", "&aЗелья", "&cВещи (причуды)"};
        Material[] icons = {Material.ENCHANTED_BOOK, Material.BREWING_STAND, Material.NETHERITE_AXE};
        for (int i = 0; i < 3; i++) {
            double scale = plugin.getConfig().getDouble("cooldowns." + groups[i] + "-scale", 1.0);
            int base = 10 + i * 2;
            inv.setItem(base, button(icons[i], names[i] + "&8: ×&f" + trim(scale),
                "&7ЛКМ — медленнее (кд ↑), ПКМ — быстрее (кд ↓)",
                "&8Меньше множитель = короче перезарядка"));
        }
    }

    private static String trim(double scale) {
        return scale == Math.floor(scale) ? String.valueOf((long) scale) : String.valueOf(scale);
    }

    private void adjustCooldown(String group, double delta) {
        String key = "cooldowns." + group + "-scale";
        double current = plugin.getConfig().getDouble(key, 1.0);
        int idx = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < SCALE_STEPS.length; i++) {
            double diff = Math.abs(SCALE_STEPS[i] - current);
            if (diff < best) { best = diff; idx = i; }
        }
        idx = Math.max(0, Math.min(SCALE_STEPS.length - 1, idx + (int) delta));
        plugin.getConfig().set(key, SCALE_STEPS[idx]);
        plugin.saveConfig();
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
                || holder instanceof ItemsHolder || holder instanceof ItemPageHolder
                || holder instanceof CooldownsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (holder instanceof RootHolder) {
            switch (slot) {
                case 10 -> openBooksMenu(player);
                case 12 -> openItemsMenu(player, 0);
                case 14 -> AdminMenu.openHub(plugin, player);
                case 16 -> openCooldownsMenu(player);
                default -> { }
            }
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
            if (slot == 45 && itemsHolder.page > 0) {
                openItemsMenu(player, itemsHolder.page - 1);
                return;
            }
            if (slot == 53 && itemsHolder.page < itemsHolder.pages - 1) {
                openItemsMenu(player, itemsHolder.page + 1);
                return;
            }
            if (slot < 0 || slot >= PER_PAGE) return;
            int index = itemsHolder.page * PER_PAGE + slot;
            if (index >= itemsHolder.items.size()) return;
            openItemPage(player, index);
        } else if (holder instanceof ItemPageHolder pageHolder) {
            if (slot == 18) {
                openItemsMenu(player, pageHolder.index / PER_PAGE);
            } else if (slot == 15) {
                ItemStack item = itemsList().get(pageHolder.index);
                player.getInventory().addItem(item).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                Msg.send(player, "gaid-given");
            }
        } else if (holder instanceof CooldownsHolder) {
            switch (slot) {
                case 22 -> openRoot(player);
                case 10, 12, 14 -> {
                    String[] groups = {"spells", "potions", "items"};
                    String group = groups[(slot - 10) / 2];
                    // ЛКМ — медленнее (шаг вверх по шкале), ПКМ — быстрее (вниз)
                    adjustCooldown(group, event.isLeftClick() ? 1 : -1);
                    Inventory inv = event.getInventory();
                    fillCooldowns(inv);
                    inv.setItem(22, button(Material.ARROW, "&7← в корень"));
                }
                default -> { }
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
        final int page;
        final int pages;
        Inventory inv;

        ItemsHolder(List<ItemStack> items, int page, int pages) {
            this.items = items;
            this.page = page;
            this.pages = pages;
        }

        @Override public Inventory getInventory() { return inv; }
    }

    private static final class ItemPageHolder implements InventoryHolder {

        final int index;
        Inventory inv;

        ItemPageHolder(int index) { this.index = index; }

        @Override public Inventory getInventory() { return inv; }
    }

    private final class CooldownsHolder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }
}

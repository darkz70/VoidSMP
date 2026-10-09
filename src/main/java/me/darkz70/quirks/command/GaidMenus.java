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
        inv.setItem(11, button(Material.BOOK, "&dОписания", "&7Механики и команды — тематические книги"));
        inv.setItem(12, button(Material.CHEST, "&eПредметы", "&7Все предметы плагина + крафты (+взять себе)"));
        inv.setItem(13, button(Material.ZOMBIE_SPAWN_EGG, "&9Призыв", "&740 отрядов мобов для ваших сцен"));
        inv.setItem(15, button(Material.PLAYER_HEAD, "&cАдмин-панель", "&7Причуды и магии онлайн-игроков"));
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
            if (java.util.Arrays.asList(BrewTree.ARROWABLE).contains(tag)) {
                ItemStack arrows = BrewTree.makeBrewArrows(brew);
                arrows.setAmount(32);
                items.add(arrows);
            }
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

    // ---------- призыв отрядов ----------

    /** 40 мобовских отрядов для сцен/клипов: [название, иконка, список тип:число…]. */
    private static final String[][] SQUADS = {
        {"Когорта: 5 зомби + 3 скелета", "ZOMBIE_SPAWN_EGG", "ZOMBIE:5", "SKELETON:3"},
        {"Стрелковый отряд: 6 скелетов + 5 пауков", "SKELETON_SPAWN_EGG", "SKELETON:6", "SPIDER:5"},
        {"Взрывной десант: 4 крипера + 2 ведьмы", "CREEPER_SPAWN_EGG", "CREEPER:4", "WITCH:2"},
        {"Бродячая орда: 8 зомби", "ZOMBIE_SPAWN_EGG", "ZOMBIE:8"},
        {"Щиты и мечи: 5 скелетов + 3 крипера", "SKELETON_SPAWN_EGG", "SKELETON:5", "CREEPER:3"},
        {"Паучье гнездо: 6 пауков + 3 пещерных", "SPIDER_SPAWN_EGG", "SPIDER:6", "CAVE_SPIDER:3"},
        {"Саббат: 4 ведьмы + 2 скелета", "WITCH_SPAWN_EGG", "WITCH:4", "SKELETON:2"},
        {"Пустынный авангард: 5 кадавров + 3 зомби", "HUSK_SPAWN_EGG", "HUSK:5", "ZOMBIE:3"},
        {"Морской дозор: 6 утопленников + 2 стража", "DROWNED_SPAWN_EGG", "DROWNED:6", "GUARDIAN:2"},
        {"Крылатая рать: 7 фантомов", "PHANTOM_SPAWN_EGG", "PHANTOM:7"},
        {"Метель: 4 заблудших + 3 скелета", "STRAY_SPAWN_EGG", "STRAY:4", "SKELETON:3"},
        {"Набег: 5 разбойников + 2 поборника", "PILLAGER_SPAWN_EGG", "PILLAGER:5", "VINDICATOR:2"},
        {"Ритуал: 3 вызывателя + 4 поборника", "EVOKER_SPAWN_EGG", "EVOKER:3", "VINDICATOR:4"},
        {"Чешуя: 6 чешуйниц + 4 эндермита", "SILVERFISH_SPAWN_EGG", "SILVERFISH:6", "ENDERMITE:4"},
        {"Тени Края: 5 эндерменов", "ENDERMAN_SPAWN_EGG", "ENDERMAN:5"},
        {"Пекло: 4 ифрита + 3 магмовых куба", "BLAZE_SPAWN_EGG", "BLAZE:4", "MAGMA_CUBE:3"},
        {"Некрогарнизон: 5 иссушителей + 2 ифрита", "WITHER_SKELETON_SPAWN_EGG", "WITHER_SKELETON:5", "BLAZE:2"},
        {"Бастион: 4 пиглина-громилы + 3 хоглина", "PIGLIN_BRUTE_SPAWN_EGG", "PIGLIN_BRUTE:4", "HOGLIN:3"},
        {"Пороховая гроза: 5 гастов", "GHAST_SPAWN_EGG", "GHAST:5"},
        {"Жвачка: 8 слаймов + 4 магмовых", "SLIME_SPAWN_EGG", "SLIME:8", "MAGMA_CUBE:4"},
        {"Глубинная триада: 6 стражей + 3 утопленника", "GUARDIAN_SPAWN_EGG", "GUARDIAN:6", "DROWNED:3"},
        {"Занавес: 4 шалкера + 2 эндермена", "SHULKER_SPAWN_EGG", "SHULKER:4", "ENDERMAN:2"},
        {"Тихий кошмар: 5 досаждателей + 2 вызывателя", "VEX_SPAWN_EGG", "VEX:5", "EVOKER:2"},
        {"Таран: 3 разорителя + 5 разбойников", "RAVAGER_SPAWN_EGG", "RAVAGER:3", "PILLAGER:5"},
        {"Труба зовёт: 1 страж + 4 голема? — просто 1 бедрок-страж", "WARDEN_SPAWN_EGG", "WARDEN:1"},
        {"Зомбипогром: 5 зомбированных пиглинов + 4 пиглина", "ZOMBIFIED_PIGLIN_SPAWN_EGG", "ZOMBIFIED_PIGLIN:5", "PIGLIN:4"},
        {"Милая ловушка: 7 пещерных пауков", "CAVE_SPIDER_SPAWN_EGG", "CAVE_SPIDER:7"},
        {"Лёд и кобыла: 3 ледяных + 2 паука", "STRAY_SPAWN_EGG", "STRAY:3", "CAVE_SPIDER:2"},
        {"Старый лес: 6 скелетов + 2 зомби-гиганта (нет, твари!)", "ZOMBIE_SPAWN_EGG", "SKELETON:6", "ZOMBIE:6"},
        {"Осада: 5 разбойников + 3 ведьмы + 2 поборника", "PILLAGER_SPAWN_EGG", "PILLAGER:5", "WITCH:3", "VINDICATOR:2"},
        {"На пастбище: 4 ведьмы + 5 зомби", "WITCH_SPAWN_EGG", "WITCH:4", "ZOMBIE:5"},
        {"Красные споры: 6 хоглинов + 4 пиглина", "HOGLIN_SPAWN_EGG", "HOGLIN:6", "PIGLIN:4"},
        {"Вересающий хор: 5 эндерменов + 3 ведьмы", "ENDERMAN_SPAWN_EGG", "ENDERMAN:5", "WITCH:3"},
        {"Шторм: 6 фантомов + 3 скелета", "PHANTOM_SPAWN_EGG", "PHANTOM:6", "SKELETON:3"},
        {"Шипастая пасть: 5 утопленников + 5 стражей", "DROWNED_SPAWN_EGG", "DROWNED:5", "GUARDIAN:5"},
        {"Крик в доме: 4 вызывателя + 4 досаждателя", "EVOKER_SPAWN_EGG", "EVOKER:4", "VEX:4"},
        {"Тихий отряд: 6 криперов + 2 скелета", "CREEPER_SPAWN_EGG", "CREEPER:6", "SKELETON:2"},
        {"Пепельный патруль: 7 иссушающих скелетов", "WITHER_SKELETON_SPAWN_EGG", "WITHER_SKELETON:7"},
        {"Козни подземки: 8 чешуйниц + 3 паука", "SILVERFISH_SPAWN_EGG", "SILVERFISH:8", "CAVE_SPIDER:3"},
        {"Финальный акт: 1 иссушитель? нет — 1 страж Края? 1 железный голем!", "IRON_GOLEM_SPAWN_EGG", "IRON_GOLEM:1", "ZOMBIE:4"},
    };

    private void openSquadsMenu(Player player, int page) {
        int pages = Math.max(1, (SQUADS.length + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        SquadsHolder holder = new SquadsHolder(page, pages);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Msg.color("&9Призыв &8(&e" + (page + 1) + "&7/&e" + pages + "&8)"));
        holder.inv = inv;
        int start = page * PER_PAGE;
        for (int slot = 0; slot < PER_PAGE && start + slot < SQUADS.length; slot++) {
            String[] squad = SQUADS[start + slot];
            inv.setItem(slot, button(Material.valueOf(squad[1]), "&9" + squad[0],
                    "&7Клик — призвать рядом с тобой"));
        }
        if (page > 0) inv.setItem(45, button(Material.ARROW, "&7← страница " + page));
        inv.setItem(49, button(Material.BARRIER, "&7← в корень"));
        if (page < pages - 1) inv.setItem(53, button(Material.ARROW, "&7страница " + (page + 2) + " →"));
        player.openInventory(inv);
    }

    private void summonSquad(Player player, int index) {
        String[] squad = SQUADS[index];
        int spawned = 0;
        for (int i = 2; i < squad.length; i++) {
            String[] pair = squad[i].split(":");
            org.bukkit.entity.EntityType type;
            try {
                type = org.bukkit.entity.EntityType.valueOf(pair[0]);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            int count = Integer.parseInt(pair[1]);
            for (int c = 0; c < count; c++) {
                double angle = Math.random() * 2 * Math.PI;
                double dist = 2 + Math.random() * 4;
                org.bukkit.Location at = player.getLocation().add(
                        Math.cos(angle) * dist, 1, Math.sin(angle) * dist);
                org.bukkit.block.Block top = player.getWorld().getHighestBlockAt(at);
                org.bukkit.entity.Entity entity = player.getWorld().spawnEntity(
                        top.getLocation().add(0.5, 1, 0.5), type);
                if (entity instanceof org.bukkit.entity.Monster monster) {
                    monster.setTarget(player);
                }
                spawned++;
            }
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation().add(0, 1, 0),
                40, 2, 1, 2, 0.05);
        Msg.send(player, "gaid-summon", "%count%", String.valueOf(spawned));
    }

    private static final class SquadsHolder implements InventoryHolder {

        final int page;
        final int pages;
        Inventory inv;

        SquadsHolder(int page, int pages) {
            this.page = page;
            this.pages = pages;
        }

        @Override public Inventory getInventory() { return inv; }
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
                || holder instanceof CooldownsHolder || holder instanceof SquadsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        if (holder instanceof RootHolder) {
            switch (slot) {
                case 11 -> openBooksMenu(player);
                case 12 -> openItemsMenu(player, 0);
                case 13 -> openSquadsMenu(player, 0);
                case 15 -> AdminMenu.openHub(plugin, player);
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
        } else if (holder instanceof SquadsHolder squads) {
            if (slot == 49) {
                openRoot(player);
                return;
            }
            if (slot == 45 && squads.page > 0) {
                openSquadsMenu(player, squads.page - 1);
                return;
            }
            if (slot == 53 && squads.page < squads.pages - 1) {
                openSquadsMenu(player, squads.page + 1);
                return;
            }
            if (slot < 0 || slot >= PER_PAGE) return;
            int index = squads.page * PER_PAGE + slot;
            if (index < SQUADS.length) summonSquad(player, index);
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

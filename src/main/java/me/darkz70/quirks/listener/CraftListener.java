package me.darkz70.quirks.listener;

import java.util.ArrayList;
import java.util.List;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.Quirk;
import me.darkz70.quirks.VoidQuirksPlugin;
import me.darkz70.quirks.magic.Element;
import me.darkz70.quirks.mechanic.BrewTree;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.Nullable;

/**
 * Крафты v1.0:
 * - Зарегистрированные (видимые): супер-удобрение (2 костной муки, только Фермер),
 *   кристалл фокуса, 6 книг стихий, 5 книг прокачки.
 * - Скрытые: ВСЕ зелья дерева — ручной матчинг сетки в PrepareItemCraftEvent (BrewTree),
 *   плюс фокусировка предмета (кристалл + не-блок) и спектральные стрелы.
 */
public final class CraftListener implements Listener {

    public static NamespacedKey fertilizerKey;

    /** Выдать/забрать видимость рецепта удобрения у игрока. */
    public static void syncFertilizerRecipe(Player player, boolean farmer) {
        if (fertilizerKey == null) return;
        if (farmer) {
            player.discoverRecipe(fertilizerKey);
        } else {
            player.undiscoverRecipe(fertilizerKey);
        }
    }

    private final VoidQuirksPlugin plugin;

    public CraftListener(VoidQuirksPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------- зарегистрированные рецепты ----------

    public static void registerRecipes(VoidQuirksPlugin plugin) {
        // зачистка старых видимых рецептов зелий (до v1.0 они были в книге)
        Bukkit.removeRecipe(new NamespacedKey(plugin, "neutral_potion"));
        Bukkit.removeRecipe(new NamespacedKey(plugin, "sculk_antidote"));

        // варёное яйцо: печь / коптильня / костёр
        addCooking(plugin, "boiled_egg_furnace",
                new org.bukkit.inventory.FurnaceRecipe(new NamespacedKey(plugin, "boiled_egg_furnace"),
                        me.darkz70.quirks.mechanic.BrewTree.makeBoiledEgg(), Material.EGG, 0.35f, 200));
        addCooking(plugin, "boiled_egg_smoking",
                new org.bukkit.inventory.SmokingRecipe(new NamespacedKey(plugin, "boiled_egg_smoking"),
                        me.darkz70.quirks.mechanic.BrewTree.makeBoiledEgg(), Material.EGG, 0.35f, 100));
        addCooking(plugin, "boiled_egg_campfire",
                new org.bukkit.inventory.CampfireRecipe(new NamespacedKey(plugin, "boiled_egg_campfire"),
                        me.darkz70.quirks.mechanic.BrewTree.makeBoiledEgg(), Material.EGG, 0.35f, 600));

        // удобрение: 2 костной муки (только Фермер видит/крафтит)
        fertilizerKey = new NamespacedKey(plugin, "super_fertilizer");
        ShapelessRecipe fert = new ShapelessRecipe(fertilizerKey, makeFertilizer());
        fert.addIngredient(Material.BONE_MEAL, 2);
        add(fertilizerKey, fert);

        // кристалл фокуса: 4 аметиста + 4 алмаза + 1 стекло
        ShapelessRecipe crystal = new ShapelessRecipe(new NamespacedKey(plugin, "focus_crystal"),
                BrewTree.makeFocusCrystal());
        crystal.addIngredient(Material.AMETHYST_SHARD, 4);
        crystal.addIngredient(Material.DIAMOND, 4);
        crystal.addIngredient(Material.GLASS);
        add(new NamespacedKey(plugin, "focus_crystal"), crystal);

        // 6 книг стихий: книга + 4 аметиста + катализатор стихии
        for (Element element : Element.values()) {
            if (element == Element.LIGHT || element == Element.DARK) {
                continue; // светлый и тёмный фолианты — только вершинами яично-океанской ветки
            }
            ShapelessRecipe book = new ShapelessRecipe(new NamespacedKey(plugin, "elem_book_" + element.id()),
                    makeElementBook(element));
            book.addIngredient(Material.BOOK);
            book.addIngredient(Material.AMETHYST_SHARD, 4);
            book.addIngredient(element.catalyst());
            add(new NamespacedKey(plugin, "elem_book_" + element.id()), book);
        }

        // книги прокачки
        ShapelessRecipe up1 = new ShapelessRecipe(new NamespacedKey(plugin, "up_book_1"), makeUpgradeBook(1));
        up1.addIngredient(Material.BOOK);
        up1.addIngredient(Material.AMETHYST_SHARD, 4);
        up1.addIngredient(Material.COPPER_BLOCK, 4);
        add(new NamespacedKey(plugin, "up_book_1"), up1);

        ShapelessRecipe up2 = new ShapelessRecipe(new NamespacedKey(plugin, "up_book_2"), makeUpgradeBook(2));
        up2.addIngredient(new RecipeChoice.ExactChoice(makeUpgradeBook(1)));
        up2.addIngredient(Material.AMETHYST_SHARD, 4);
        up2.addIngredient(Material.IRON_BLOCK, 4);
        add(new NamespacedKey(plugin, "up_book_2"), up2);

        ShapelessRecipe up3 = new ShapelessRecipe(new NamespacedKey(plugin, "up_book_3"), makeUpgradeBook(3));
        up3.addIngredient(Material.BOOK); // plain BOOK — так в спеке
        up3.addIngredient(Material.AMETHYST_SHARD, 4);
        up3.addIngredient(Material.GOLD_BLOCK, 4);
        add(new NamespacedKey(plugin, "up_book_3"), up3);

        ShapelessRecipe up4 = new ShapelessRecipe(new NamespacedKey(plugin, "up_book_4"), makeUpgradeBook(4));
        up4.addIngredient(new RecipeChoice.ExactChoice(makeUpgradeBook(3)));
        up4.addIngredient(Material.AMETHYST_SHARD, 4);
        up4.addIngredient(Material.OBSIDIAN, 4);
        add(new NamespacedKey(plugin, "up_book_4"), up4);

        ShapelessRecipe up5 = new ShapelessRecipe(new NamespacedKey(plugin, "up_book_5"), makeUpgradeBook(5));
        up5.addIngredient(new RecipeChoice.ExactChoice(makeUpgradeBook(4)));
        up5.addIngredient(Material.AMETHYST_SHARD, 4);
        up5.addIngredient(Material.DIAMOND_BLOCK, 4);
        add(new NamespacedKey(plugin, "up_book_5"), up5);
    }

    private static void addCooking(VoidQuirksPlugin plugin, String id, org.bukkit.inventory.CookingRecipe<?> recipe) {
        NamespacedKey key = new NamespacedKey(plugin, id);
        if (Bukkit.getRecipe(key) != null) Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
    }

    private static void add(NamespacedKey key, ShapelessRecipe recipe) {
        if (Bukkit.getRecipe(key) != null) Bukkit.removeRecipe(key);
        Bukkit.addRecipe(recipe);
    }

    // ---------- предметы ----------

    private static ItemStack basePotion(PotionType type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    /** Зелье «без эффекта» — база дерева. Рецепт скрыт (крафтится ручным матчингом). */
    public static ItemStack makeNeutral() {
        ItemStack item = basePotion(PotionType.WATER);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.displayName(Msg.color("&7Нейтрализованное зелье"));
        meta.lore(List.of(Msg.color("&7Тихое, безвредное, почти бесполезное.")));
        meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, "neutral");
        var cmdneutral = meta.getCustomModelDataComponent();
        cmdneutral.setStrings(List.of("potion_neutral"));
        meta.setCustomModelDataComponent(cmdneutral);
        item.setItemMeta(meta);
        return item;
    }

    /** Антидот от скалка. Рецепт скрыт. */
    public static ItemStack makeAntidote() {
        ItemStack item = basePotion(PotionType.AWKWARD);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.displayName(Msg.color("&2Антидот от скалка"));
        meta.lore(List.of(Msg.color("&7Выпей, чтобы изгнать Скалк любого уровня.")));
        meta.setColor(Color.fromRGB(0x3B, 0xD3, 0x7A));
        meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, "antidote");
        var cmdantidote = meta.getCustomModelDataComponent();
        cmdantidote.setStrings(List.of("potion_antidote"));
        meta.setCustomModelDataComponent(cmdantidote);
        item.setItemMeta(meta);
        return item;
    }

    /** Супер-удобрение Фермера — как 6 костной муки за раз. */
    public static ItemStack makeFertilizer() {
        ItemStack item = new ItemStack(Material.BONE_MEAL);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&6Супер-удобрение"));
            meta.lore(List.of(Msg.color("&7Работает как 6 костной муки за один клик.")));
            meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, "fertilizer");
        });
        return item;
    }

    /** Книга изучения стихии. */
    public static ItemStack makeElementBook(Element element) {
        ItemStack item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&bКнига стихии: " + element.emoji() + " &d" + element.display()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Msg.color("&7ПКМ — изучить стихию."));
            if (element.requiresFourBooks()) {
                lore.add(Msg.color("&8Нужны 4 книги других стихий в инвентаре."));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING,
                    "elem-book-" + element.id());
            var cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("book_" + element.id()));
            meta.setCustomModelDataComponent(cmd);
        });
        return item;
    }

    /** Книга прокачки уровня магии. */
    public static ItemStack makeUpgradeBook(int tier) {
        int min = switch (tier) {
            case 1 -> 1;
            case 2 -> 10;
            case 3 -> 20;
            case 4 -> 30;
            default -> 40;
        };
        String[] names = {"", "&fМедная книга прокачки", "&7Железная книга прокачки",
            "&eЗолотая книга прокачки", "&8Обсидиановая книга прокачки", "&bАлмазная книга прокачки"};
        ItemStack item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            meta.displayName(Msg.color(names[tier] + " &7(от " + min + " ур.)"));
            meta.lore(List.of(Msg.color("&7ПКМ — +1 уровень магии.")));
            meta.getPersistentDataContainer().set(Keys.brewMark, PersistentDataType.STRING, "upbook-" + tier);
            var cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("upbook_" + tier));
            meta.setCustomModelDataComponent(cmd);
        });
        return item;
    }

    // ---------- Prepare: скрытые рецепты ----------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        inventory.setResult(null); // иначе возможны «залипшие» результаты при ручном матчинге
        if (!(event.getView().getPlayer() instanceof Player player)) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : inventory.getMatrix()) {
            if (stack != null && !stack.getType().isAir()) stacks.add(stack);
        }

        // 1) фокусировка предмета
        ItemStack fusion = BrewTree.focusFusion(stacks);
        if (fusion != null) {
            inventory.setResult(fusion);
            return;
        }

        // 2) дерево зелий — скрытый матчинг
        ItemStack brew = BrewTree.match(stacks);
        if (brew != null) {
            String tag = BrewTree.markOf(brew);
            // нейтралка и основа — только магам 1+ и Админам
            if (("neutral".equals(tag) || "basis".equals(tag)) && !seesSecretBasics(player)) {
                return;
            }
            inventory.setResult(brew);
            return;
        }

        // 3) ванильный результат выше был сброшен — вернём его из рецепта,
        //    но удобрение не-фермеру не показываем
        ItemStack serverResult = event.getRecipe() == null ? null : event.getRecipe().getResult();
        if (serverResult != null) {
            if (BrewTree.tagged(serverResult, "fertilizer")
                    && plugin.quirks().levelOf(player, Quirk.FARMER) == 0
                    && plugin.quirks().levelOf(player, Quirk.ADMIN) == 0) {
                return; // не-фермер (и не Админ) не видит удобрение
            }
            String tag = BrewTree.markOf(serverResult);
            if (tag != null && tag.startsWith("upbook-") && !seesSecretBasics(player)) {
                return; // книги прокачки — только магам и Админам
            }
            inventory.setResult(serverResult);
        }
    }

    /** Маги (ур. 1+) и причуда Админ могут видеть/делать нейтралку, основу и улучшатели. */
    private boolean seesSecretBasics(Player player) {
        if (plugin.quirks().levelOf(player, Quirk.ADMIN) > 0) return true;
        var data = plugin.magic().data(player);
        return data != null && data.magicElement() != null && data.magicLevel() >= 1;
    }

    /** Супер-удобрение крафтит только Фермер. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getInventory().getResult();
        if (BrewTree.tagged(result, "fertilizer") && plugin.quirks().levelOf(player, Quirk.FARMER) == 0) {
            event.setCancelled(true);
            Msg.send(player, "farmer-craft-denied");
            return;
        }
        // яично-океанская тройка: светлый фолиант ложится стеком, тёмный — бонусом
        if (result != null && "duo:dark".equals(result.getPersistentDataContainer()
                .get(Keys.brewTarget, org.bukkit.persistence.PersistentDataType.STRING))) {
            player.getInventory().addItem(makeElementBook(Element.DARK)).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            Msg.send(player, "foliants-duo");
        }
    }

    /** Супер-удобрение: ПКМ по грядке — 6 применений костной муки. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!BrewTree.tagged(item, "fertilizer")) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        if (!me.darkz70.quirks.util.ScanUtil.tryUse(player.getUniqueId(), "fertilizer", 200)) return;

        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);

        for (int i = 0; i < 6; i++) {
            clicked.applyBoneMeal(event.getBlockFace());
        }
        item.setAmount(item.getAmount() - 1);
        player.swingMainHand();
        player.getWorld().playSound(clicked.getLocation(), Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.2f);
    }
}

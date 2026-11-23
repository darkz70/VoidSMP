package me.darkz70.quirks.mechanic;

import java.util.List;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Реликвии (#98): четыре особых, помеченных PDC предмета — их нельзя перепутать
 * с обычными ванильными блоками того же материала (структурный блок/блок пустоты/
 * магма/призмарин существуют на сервере и без нашей метки, и трогать их не нужно).
 * Пятый особый предмет — обычное ванильное «Яйцо дракона» — меткой не снабжается
 * (см. RelicTask): эффект действует на любое яйцо дракона на сервере.
 */
public final class RelicItems {

    private RelicItems() {}

    // ---------------------------------------------------------------- Обложка конструктора миров

    public static ItemStack makeWorldbuilderCover() {
        ItemStack item = new ItemStack(Material.STRUCTURE_BLOCK);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&5&l✦ Обложка конструктора миров"));
            meta.lore(List.of(
                    Msg.color("&7&oЧувствуется, сила определяющая границу мироздания"),
                    Msg.color("&8—————————————"),
                    Msg.color("&7Нельзя поставить или сломать."),
                    Msg.color("&dВ инвентаре: &fСопротивление II &7+ &fЗамедление II&7 (постоянно)."),
                    Msg.color("&dПри смерти: &7телепорт на место смерти через 10 сек (КД 10 мин)."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.relicCover, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isWorldbuilderCover(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.STRUCTURE_BLOCK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.relicCover);
    }

    // ---------------------------------------------------------------- Блок пустоты

    public static ItemStack makeVoidBlock() {
        ItemStack item = new ItemStack(Material.STRUCTURE_VOID);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&3&l✦ Блок пустоты"));
            meta.lore(List.of(
                    Msg.color("&7&oЧувствуется, что принадлежал гениальному существу"),
                    Msg.color("&8—————————————"),
                    Msg.color("&7Нельзя поставить или сломать."),
                    Msg.color("&dВ инвентаре: &7открывает крафты &fИнженера&7 и &fФермера&7."),
                    Msg.color("&dВ инвентаре: &7полная защита от заморозки."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.relicVoidBlock, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isVoidBlock(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.STRUCTURE_VOID && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.relicVoidBlock);
    }

    // ---------------------------------------------------------------- Лавовое ядро

    public static ItemStack makeLavaCore() {
        ItemStack item = new ItemStack(Material.MAGMA_BLOCK);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&6&l✦ Лавовое ядро"));
            meta.lore(List.of(
                    Msg.color("&7&oПульсирующее сердце огненной бездны"),
                    Msg.color("&8—————————————"),
                    Msg.color("&7Будучи поставленным — нельзя сломать."),
                    Msg.color("&dКупол 32 блока: &7лёд в инвентарях испаряется в пар,"),
                    Msg.color("&7вода и лёд в радиусе испаряются, костры зажигаются,"),
                    Msg.color("&7носителей &fБлока пустоты&7 отталкивает на входе."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.relicLavaCore, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isLavaCore(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.MAGMA_BLOCK && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.relicLavaCore);
    }

    // ---------------------------------------------------------------- Призма

    public static ItemStack makePrism() {
        ItemStack item = new ItemStack(Material.PRISMARINE);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&b&l✦ Призма"));
            meta.lore(List.of(
                    Msg.color("&7&oЗастывший свет глубинных храмов"),
                    Msg.color("&8—————————————"),
                    Msg.color("&7Будучи поставленной — нельзя сломать."),
                    Msg.color("&dВ радиусе 32 блока: &7лава становится обсидианом,"),
                    Msg.color("&7магма становится камнем."),
                    Msg.color("&dНосителям &fБлока пустоты&7 в радиусе: &fРегенерация I &7+ &fСкорость II&7."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.relicPrism, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isPrism(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.PRISMARINE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.relicPrism);
    }

    // ---------------------------------------------------------------- Nevergate's Mace (#99)

    public static ItemStack makeNevergateMace() {
        ItemStack item = new ItemStack(Material.MACE);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&4&l☠ Nevergate's Mace"));
            meta.lore(List.of(
                    Msg.color("&7&oОружие из-за врат, что никогда не откроются вновь"),
                    Msg.color("&8—————————————"),
                    Msg.color("&cУрон: &7×5 от обычной булавы."),
                    Msg.color("&dПри попадании: &7тряска экрана всем в радиусе 50 блоков,"),
                    Msg.color("&7рядом с ударом пропадает от 3 до 15 блоков."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.relicNevergateMace, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isNevergateMace(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.MACE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.relicNevergateMace);
    }

    /** Та же булава, но с подвохом: исчезает после одного удара. Отличается только
     *  регистром имени (намеренно — «nevergate's mace» с маленькой буквы). */
    public static ItemStack makeNevergateMaceOnce() {
        ItemStack item = new ItemStack(Material.MACE);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&4&l☠ nevergate's mace"));
            meta.lore(List.of(
                    Msg.color("&7&oОдин удар — и врата закрываются за ней навсегда"),
                    Msg.color("&8—————————————"),
                    Msg.color("&cУрон: &7×5 от обычной булавы."),
                    Msg.color("&dПри попадании: &7тряска экрана всем в радиусе 50 блоков,"),
                    Msg.color("&7рядом с ударом пропадает от 3 до 15 блоков."),
                    Msg.color("&4Исчезает после первого же удара."),
                    Msg.color("&8VoidSMP")
            ));
            meta.getPersistentDataContainer().set(Keys.relicNevergateMaceOnce, PersistentDataType.BOOLEAN, true);
        });
        return item;
    }

    public static boolean isNevergateMaceOnce(@Nullable ItemStack item) {
        return item != null && item.getType() == Material.MACE && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.relicNevergateMaceOnce);
    }

    // ---------------------------------------------------------------- Проверки по всему инвентарю

    /** Предмет-реликвия лежит у игрока где угодно в инвентаре (хотбар/рюкзак/броня/офф-рука). */
    public static boolean carries(PlayerInventory inv, java.util.function.Predicate<ItemStack> check) {
        // getContents() у PlayerInventory — только хотбар+рюкзак (36 слотов), БЕЗ брони/офф-руки
        // (это особенность реализации Bukkit) — проверяем их отдельно.
        for (ItemStack stack : inv.getContents()) {
            if (check.test(stack)) return true;
        }
        for (ItemStack stack : inv.getArmorContents()) {
            if (check.test(stack)) return true;
        }
        return check.test(inv.getItemInOffHand());
    }

    public static boolean hasCover(PlayerInventory inv) {
        return carries(inv, RelicItems::isWorldbuilderCover);
    }

    public static boolean hasVoidBlock(PlayerInventory inv) {
        return carries(inv, RelicItems::isVoidBlock);
    }
}

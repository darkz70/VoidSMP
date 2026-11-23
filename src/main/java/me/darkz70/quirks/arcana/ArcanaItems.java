package me.darkz70.quirks.arcana;

import java.util.List;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * Предметы НОВОЙ системы магии Arcana (#99, причуда MAGE): посох-фокус
 * и 12 фолиантов стихий. Полностью отдельно от старых elem-book- и фокус-кристалла
 * (та старая система теперь на технической причуде ADMIN_MAG).
 */
public final class ArcanaItems {

    private ArcanaItems() {}

    /** Посох Arcana — фокус-предмет для ПКМ-каста/Q/F/Shift+Q/Shift+F новой магии. */
    public static ItemStack makeFocusStaff() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&d&l✦ Посох Арканы"));
            meta.lore(List.of(
                    Msg.color("&7&oПроводник новой, 12-стихийной магии"),
                    Msg.color("&8—————————————"),
                    Msg.color("&dПКМ: &7каст текущего заклинания"),
                    Msg.color("&dQ / F: &7след. заклинание &8| &dShift+F: &7меню"),
                    Msg.color("&dShift+Q: &7сменить активную стихию"),
                    Msg.color("&8Требует причуду &5Маг&8. VoidSMP"))
            );
            meta.getPersistentDataContainer().set(Keys.arcanaFocus, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isFocusStaff(@Nullable ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(Keys.arcanaFocus, PersistentDataType.BYTE);
    }

    /** Фолиант стихии: ПКМ изучает стихию (1 ур.) или сообщает об ограничении. */
    public static ItemStack makeTome(Arcana element) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&5&l" + element.emoji() + " Фолиант: " + element.display()));
            meta.lore(List.of(
                    Msg.color("&7&oДревние письмена об Аркане стихии " + element.display().toLowerCase()),
                    Msg.color("&8—————————————"),
                    Msg.color("&dПКМ: &7изучить стихию (нужна причуда &5Маг&7)"),
                    Msg.color("&8Вторую и дальнейшие стихии можно изучить"),
                    Msg.color("&8только имея хотя бы одну 20-го уровня."),
                    Msg.color("&8VoidSMP"))
            );
            meta.getPersistentDataContainer().set(Keys.arcanaBook, PersistentDataType.STRING, element.id());
        });
        return item;
    }

    @Nullable
    public static Arcana tomeElement(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.arcanaBook, PersistentDataType.STRING);
        return Arcana.byId(id);
    }
}

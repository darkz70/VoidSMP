package me.darkz70.quirks.mechanic;

import java.util.List;
import me.darkz70.quirks.Keys;
import me.darkz70.quirks.util.Msg;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Скалк-древесина — НАСТОЯЩИЙ новый блок (не перекрашенное ванильное дерево),
 * реализован через редкие, никогда не встречающиеся в обычной игре сочетания
 * состояний NOTE_BLOCK (instrument+note+powered). Ресурспак подменяет модель
 * ТОЛЬКО для этих трёх конкретных сочетаний — все остальные 1347 комбинаций
 * нотного блока выглядят и работают как обычно, так что на любой другой
 * нотный блок на сервере это никак не влияет.
 * <p>
 * Выбраны намеренно редкие инструменты, которые получаются только от голов
 * мобов (крипер/дракон/иссохший скелет) — их почти никогда не ставят под
 * нотный блок специально, так что случайное совпадение исключено.
 */
public final class SculkWood {

    public enum Type {
        LOG("log", Instrument.CREEPER, "&2&lСкалк-бревно", Msg.color("&7Ствол скалк-дерева.")),
        LEAVES("leaves", Instrument.DRAGON, "&2&lСкалк-листва", Msg.color("&7Крона скалк-дерева.")),
        PLANKS("planks", Instrument.WITHER_SKELETON, "&2&lСкалк-доски",
                Msg.color("&7Доски из скалк-древесины."));

        public final String id;
        public final Instrument instrument;
        public final String displayName;
        public final net.kyori.adventure.text.Component lore;

        Type(String id, Instrument instrument, String displayName, net.kyori.adventure.text.Component lore) {
            this.id = id;
            this.instrument = instrument;
            this.displayName = displayName;
            this.lore = lore;
        }

        public static Type byId(String id) {
            for (Type t : values()) {
                if (t.id.equals(id)) return t;
            }
            return null;
        }
    }

    private SculkWood() {}

    /** Ставит блок скалк-древесины нужного типа в мире (используется генерацией острова/дерева). */
    public static void place(Block block, Type type) {
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(type.instrument);
        data.setNote(new Note(0));
        data.setPowered(false);
        block.setBlockData(data, false); // без applyPhysics — не даём соседям пересчитать инструмент
    }

    /** Проверяет, что блок — именно наш скалк-древесный вариант (и какой). */
    public static Type typeOf(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) return null;
        BlockData bd = block.getBlockData();
        if (!(bd instanceof NoteBlock note)) return null;
        if (note.isPowered() || note.getNote().getId() != 0) return null;
        for (Type t : Type.values()) {
            if (note.getInstrument() == t.instrument) return t;
        }
        return null;
    }

    /** Выдаваемый предмет (для /gaid → предметы и для дропа при ломании). */
    public static ItemStack makeItem(Type type) {
        ItemStack item = new ItemStack(Material.NOTE_BLOCK);
        item.editMeta(meta -> {
            meta.displayName(Msg.color(type.displayName));
            meta.lore(List.of(type.lore));
            meta.getPersistentDataContainer().set(Keys.sculkWoodType, PersistentDataType.STRING, type.id);
            var cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("sculk_" + type.id));
            meta.setCustomModelDataComponent(cmd);
        });
        return item;
    }

    /** Тип скалк-древесины, записанный в предмете (если это он), иначе null. */
    public static Type typeOfItem(ItemStack item) {
        if (item == null || item.getType() != Material.NOTE_BLOCK || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.sculkWoodType, PersistentDataType.STRING);
        return id == null ? null : Type.byId(id);
    }
}

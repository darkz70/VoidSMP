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

    /** Узлы скалк-древесины в мире — для усиления радиуса скалк-сенсоров рядом. */
    private static final java.util.Set<org.bukkit.Location> NODES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private SculkWood() {}

    /** Ставит блок скалк-древесины нужного типа в мире (используется генерацией острова/дерева). */
    public static void place(Block block, Type type) {
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(type.instrument);
        data.setNote(new Note(0));
        data.setPowered(false);
        block.setBlockData(data, false); // без applyPhysics — не даём соседям пересчитать инструмент
        NODES.add(block.getLocation());
    }

    /** Забыть узел (вызывается при ломании блока). */
    public static void forget(Block block) {
        NODES.remove(block.getLocation());
    }

    /** Есть ли узел скалк-древесины в радиусе от точки — усиливает скалк-сенсоры рядом. */
    public static boolean hasNodeNear(org.bukkit.Location loc, double radius) {
        if (NODES.isEmpty()) return false;
        double r2 = radius * radius;
        for (org.bukkit.Location n : NODES) {
            if (n.getWorld() == null || !n.getWorld().equals(loc.getWorld())) continue;
            if (n.distanceSquared(loc) <= r2) return true;
        }
        return false;
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

    // ---------- скалк-саженец (отдельный предмет, НЕ настоящий дуб-саженец) ----------

    /**
     * Скалк-саженец — отдельный предмет на базе OAK_SAPLING (материал переиспользован
     * только как носитель, по умолчанию без своей пометки он выглядит и остаётся
     * обычным ванильным саженцем дуба; наш — отличается CustomModelData + PDC-меткой,
     * как и остальные магические предметы плагина). ПКМ по земле — мгновенно выращивает
     * скалк-дерево (см. GaidFx.onSaplingUse), а не растёт как ванильный саженец.
     */
    public static ItemStack makeSaplingItem() {
        ItemStack item = new ItemStack(Material.OAK_SAPLING);
        item.editMeta(meta -> {
            meta.displayName(Msg.color("&2&lСкалк-саженец"));
            meta.lore(List.of(Msg.color("&7ПКМ по земле — мгновенно вырастает скалк-дерево.")));
            meta.getPersistentDataContainer().set(Keys.sculkWoodType, PersistentDataType.STRING, "sapling");
            var cmd = meta.getCustomModelDataComponent();
            cmd.setStrings(List.of("sculk_sapling"));
            meta.setCustomModelDataComponent(cmd);
        });
        return item;
    }

    /** Это именно наш скалк-саженец (а не обычный дубовый)? */
    public static boolean isSaplingItem(ItemStack item) {
        if (item == null || item.getType() != Material.OAK_SAPLING || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(Keys.sculkWoodType, PersistentDataType.STRING);
        return "sapling".equals(id);
    }
}

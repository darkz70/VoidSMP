package me.darkz70.quirks.mechanic.customblock;

import org.bukkit.Material;

/**
 * Описание одного "кастомного блока" VoidQuirks.
 *
 * Важная техническая правда (чтобы не было иллюзий): ни один плагин под Spigot/Paper
 * — ни VoidQuirks, ни платный ExecutableBlocks, ни любой другой — физически НЕ может
 * зарегистрировать в клиенте Minecraft новый, доселе не существовавший id блока.
 * Ванильный протокол/рендер знает только блоки из своего реестра. Единственный способ
 * получить "свой" блок без мода/Forge/Fabric — взять существующий id, перекрасить его
 * текстуру через ресурспак и управлять поведением (хитбокс/разрушаемость/вид) из кода.
 * Именно так устроен ExecutableBlocks изнутри (он тоже садится на реальные id блоков —
 * обычно NOTE_BLOCK/MUSHROOM_BLOCK и т.п. — и добавляет сверху свою логику), и именно
 * так устроена наша система: реестр "типов" с привязкой к базовому материалу + своя
 * текстура, но без закорючек от самого материала (хитбокс/разрушение берём на себя
 * через BlockDisplay — см. CustomBlockService).
 */
public record CustomBlockType(String id, String label, Material base) {

    /** Декоративный/небоевой блок без коллизии и без возможности сломать — BlockDisplay (CustomBlockService). */
    public static final CustomBlockType SCULK_WARD =
            new CustomBlockType("sculk_ward", "Скалк-страж (стена купола)", Material.STRUCTURE_VOID);

    /**
     * Скалк-древесина — тоже "кастомный блок" VoidQuirks, но другого рода: это НАСТОЯЩИЙ
     * ставящийся/рубящийся блок (редкая комбинация instrument+note+powered у NOTE_BLOCK,
     * см. SculkWood.java), а не BlockDisplay. Здесь регистрируется только для единого
     * списка/документации (`/quirk block list`) — реальным размещением занимается SculkWood,
     * а НЕ CustomBlockService.
     */
    public static final CustomBlockType SCULK_LOG =
            new CustomBlockType("sculk_log", "Скалк-бревно (настоящий блок, NOTE_BLOCK)", Material.NOTE_BLOCK);
    public static final CustomBlockType SCULK_LEAVES =
            new CustomBlockType("sculk_leaves", "Скалк-листва (настоящий блок, NOTE_BLOCK)", Material.NOTE_BLOCK);
    public static final CustomBlockType SCULK_PLANKS =
            new CustomBlockType("sculk_planks", "Скалк-доски (настоящий блок, NOTE_BLOCK)", Material.NOTE_BLOCK);
}

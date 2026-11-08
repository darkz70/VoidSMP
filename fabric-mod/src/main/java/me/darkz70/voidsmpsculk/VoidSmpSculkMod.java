package me.darkz70.voidsmpsculk;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * VoidSMP: Скалк (Fabric) — наши два скалк-биома (остров и цитадель), настоящая
 * скалк-древесина (лог/листва/доски/саженец) как отдельные блоки/предметы (не трюк
 * с нотным блоком, как в серверном плагине — здесь это НАСТОЯЩИЕ новые id блоков,
 * потому что Fabric-мод, в отличие от плагина Paper, может регистрировать новые
 * блоки), и команда /sculkvillage — мгновенно поднимает деревню и перекрашивает
 * дубовую древесину рядом в скалк-древесину.
 *
 * Биомы (data/voidquirks/worldgen/biome/*.json) взяты 1:1 из серверного датапака
 * VoidQuirks — тот же "voidquirks:sculk_island" / "voidquirks:sculk_citadel".
 */
public final class VoidSmpSculkMod implements ModInitializer {

    public static final String MOD_ID = "voidquirks";

    public static final Block SCULK_LOG = new RotatedPillarBlock(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(2.0f)
                    .sound(SoundType.SCULK)
                    .requiresCorrectToolForDrops());

    public static final Block SCULK_LEAVES = new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(0.2f)
                    .sound(SoundType.SCULK)
                    .noOcclusion());

    public static final Block SCULK_PLANKS = new Block(
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(2.0f)
                    .sound(SoundType.SCULK)
                    .requiresCorrectToolForDrops());

    public static Item SCULK_SAPLING_ITEM;

    @Override
    public void onInitialize() {
        register("sculk_log", SCULK_LOG);
        register("sculk_leaves", SCULK_LEAVES);
        register("sculk_planks", SCULK_PLANKS);

        SCULK_SAPLING_ITEM = Registry.register(BuiltInRegistries.ITEM, id("sculk_sapling"),
                new Item(new Item.Properties()));

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> {
            entries.accept(SCULK_LOG);
            entries.accept(SCULK_LEAVES);
            entries.accept(SCULK_PLANKS);
        });
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.NATURAL_BLOCKS).register(entries ->
                entries.accept(SCULK_SAPLING_ITEM));

        SculkVillageCommand.register();
    }

    private static void register(String path, Block block) {
        Registry.register(BuiltInRegistries.BLOCK, id(path), block);
        Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, new Item.Properties()));
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}

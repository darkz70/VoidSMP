package me.darkz70.voidsmpsculk;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * /sculkvillage — мгновенно поднимает обычную ванильную деревню (через штатную
 * команду /place structure — ничего не переизобретаем) и сразу "скалкует" дубовую
 * древесину в радиусе: OAK_LOG/OAK_PLANKS/OAK_LEAVES меняются на наши сами
 * VoidSmpSculkMod.SCULK_* блоки. Админ-команда (permission level 2, как у /give).
 */
public final class SculkVillageCommand {

    private static final int RADIUS_XZ = 48;
    private static final int RADIUS_DOWN = 8;
    private static final int RADIUS_UP = 24;

    private SculkVillageCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("sculkvillage")
                        .requires(src -> src.hasPermission(2))
                        .executes(SculkVillageCommand::run)));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Только для игрока."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = player.blockPosition();

        // 1) ванильная деревня мгновенно — переиспользуем штатную /place structure,
        //    ничего своего для генерации структур не пишем.
        source.getServer().getCommands().performPrefixedCommand(source,
                "place structure minecraft:village_plains " + pos.getX() + " " + pos.getY() + " " + pos.getZ());

        // 2) "скалкуем" дубовую древесину рядом — деревня визуально становится скалковой.
        int replaced = sculkify(level, pos);

        source.sendSuccess(() -> Component.literal(
                "✦ Скалк-деревня поднялась из земли (" + replaced + " блоков перекрашено)."), true);
        return 1;
    }

    private static int sculkify(ServerLevel level, BlockPos center) {
        int replaced = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -RADIUS_XZ; x <= RADIUS_XZ; x++) {
            for (int z = -RADIUS_XZ; z <= RADIUS_XZ; z++) {
                for (int y = -RADIUS_DOWN; y <= RADIUS_UP; y++) {
                    cursor.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(cursor);
                    BlockState next = sculkEquivalent(state);
                    if (next != null) {
                        level.setBlockAndUpdate(cursor.immutable(), next);
                        replaced++;
                    }
                }
            }
        }
        return replaced;
    }

    private static BlockState sculkEquivalent(BlockState state) {
        if (state.is(Blocks.OAK_LOG) || state.is(Blocks.OAK_WOOD)) {
            return VoidSmpSculkMod.SCULK_LOG.defaultBlockState();
        }
        if (state.is(Blocks.OAK_PLANKS)) {
            return VoidSmpSculkMod.SCULK_PLANKS.defaultBlockState();
        }
        if (state.is(Blocks.OAK_LEAVES)) {
            return VoidSmpSculkMod.SCULK_LEAVES.defaultBlockState();
        }
        return null;
    }
}

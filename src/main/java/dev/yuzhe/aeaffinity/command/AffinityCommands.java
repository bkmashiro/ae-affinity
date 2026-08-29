package dev.yuzhe.aeaffinity.command;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import com.mojang.brigadier.CommandDispatcher;
import dev.yuzhe.aeaffinity.grid.IAffinityGridService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class AffinityCommands {
    private AffinityCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aeaffinity")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("enable")
                        .executes(context -> setAnchorAtLook(context.getSource(), true))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> setAnchor(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        true))))
                .then(Commands.literal("disable")
                        .executes(context -> setAnchorAtLook(context.getSource(), false))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> setAnchor(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                        false))))
                .then(Commands.literal("status")
                        .executes(context -> statusAtLook(context.getSource()))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> status(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(context, "pos"))))));
    }

    private static int setAnchorAtLook(CommandSourceStack source, boolean enabled) {
        var pos = lookedAtBlock(source);
        return pos == null ? 0 : setAnchor(source, pos, enabled);
    }

    private static int statusAtLook(CommandSourceStack source) {
        var pos = lookedAtBlock(source);
        return pos == null ? 0 : status(source, pos);
    }

    private static BlockPos lookedAtBlock(CommandSourceStack source) {
        var player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("A player must look at an AE block, or supply <x> <y> <z>."));
            return null;
        }
        var hit = player.pick(8.0, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            source.sendFailure(Component.literal("Look at an AE block within 8 blocks."));
            return null;
        }
        return ((BlockHitResult) hit).getBlockPos();
    }

    private static int setAnchor(CommandSourceStack source, BlockPos pos, boolean enabled) {
        var node = findNode(source.getLevel(), pos);
        if (node == null) {
            source.sendFailure(Component.literal("No active AE node at " + pos.toShortString()));
            return 0;
        }

        var service = node.getGrid().getService(IAffinityGridService.class);
        if (enabled) {
            service.addAnchor(node);
        } else {
            service.removeAnchor(node);
        }
        markChanged(source.getLevel(), pos);
        source.sendSuccess(
                () -> Component.literal("AE Affinity " + (enabled ? "enabled" : "disabled")
                        + " for the grid at " + pos.toShortString()),
                true);
        return 1;
    }

    private static int status(CommandSourceStack source, BlockPos pos) {
        var node = findNode(source.getLevel(), pos);
        if (node == null) {
            source.sendFailure(Component.literal("No active AE node at " + pos.toShortString()));
            return 0;
        }
        var service = node.getGrid().getService(IAffinityGridService.class);
        source.sendSuccess(
                () -> Component.literal("AE Affinity: anchored=" + service.hasAnchor()
                        + ", mountedEndpoints=" + service.mountedEndpointCount()),
                false);
        return 1;
    }

    private static IGridNode findNode(ServerLevel level, BlockPos pos) {
        var host = GridHelper.getNodeHost(level, pos);
        if (host == null) {
            return null;
        }
        for (var side : Direction.values()) {
            var node = host.getGridNode(side);
            if (node != null) {
                return node;
            }
        }
        return host.getGridNode(null);
    }

    private static void markChanged(ServerLevel level, BlockPos pos) {
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }
}

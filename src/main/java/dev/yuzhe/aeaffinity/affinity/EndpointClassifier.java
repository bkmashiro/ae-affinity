package dev.yuzhe.aeaffinity.affinity;

import appeng.api.AECapabilities;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.core.definitions.AEItems;
import appeng.me.cells.BasicCellInventory;
import appeng.parts.storagebus.StorageBusPart;
import dev.yuzhe.aeaffinity.endpoint.MountedEndpoint;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;

/** Structural classification only. Unknown and nested ME storage remains opaque until it can report aggregate affinity. */
public final class EndpointClassifier {
    private EndpointClassifier() {
    }

    public static EndpointKind kind(MountedEndpoint endpoint) {
        if (endpoint.provider() instanceof IChestOrDrive drive) {
            return hasOnlyKnownCells(drive) ? EndpointKind.CELL : EndpointKind.OPAQUE;
        }
        if (endpoint.provider() instanceof StorageBusPart bus && isDirectSlottedStorage(bus)) {
            return EndpointKind.SLOTTED;
        }
        return EndpointKind.OPAQUE;
    }

    public static boolean isConservativeTarget(MountedEndpoint endpoint) {
        if (endpoint.provider() instanceof StorageBusPart bus) {
            return !bus.getUpgrades().isInstalled(AEItems.VOID_CARD)
                    && access(bus).isAllowInsertion();
        }
        return endpoint.provider() instanceof IChestOrDrive drive
                && hasOnlyKnownCells(drive)
                && !hasVoidCell(drive);
    }

    public static boolean isRollbackSafeSource(MountedEndpoint endpoint) {
        if (endpoint.provider() instanceof StorageBusPart bus) {
            var access = access(bus);
            return access.isAllowExtraction()
                    && access.isAllowInsertion()
                    && !bus.getUpgrades().isInstalled(AEItems.VOID_CARD);
        }
        return endpoint.provider() instanceof IChestOrDrive drive
                && hasOnlyKnownCells(drive)
                && !hasVoidCell(drive);
    }

    private static AccessRestriction access(StorageBusPart bus) {
        return bus.getConfigManager().getSetting(Settings.ACCESS);
    }

    private static boolean isDirectSlottedStorage(StorageBusPart bus) {
        if (!(bus.getLevel() instanceof ServerLevel level) || bus.getSide() == null) {
            return false;
        }
        var context = bus.getSide().getOpposite();
        var target = bus.getBlockEntity().getBlockPos().relative(bus.getSide());

        if (level.getCapability(AECapabilities.ME_STORAGE, target, context) != null) {
            return false;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK, target, context) != null;
    }

    private static boolean hasOnlyKnownCells(IChestOrDrive drive) {
        for (int slot = 0; slot < drive.getCellCount(); slot++) {
            var cell = drive.getOriginalCellInventory(slot);
            if (cell != null && !(cell instanceof BasicCellInventory)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasVoidCell(IChestOrDrive drive) {
        for (int slot = 0; slot < drive.getCellCount(); slot++) {
            var cell = drive.getOriginalCellInventory(slot);
            if (cell instanceof BasicCellInventory basicCell
                    && basicCell.getUpgradesInventory().isInstalled(AEItems.VOID_CARD)) {
                return true;
            }
        }
        return false;
    }
}

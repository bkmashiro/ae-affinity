package dev.yuzhe.aeaffinity.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

/** Performs one complete, synchronous, non-yielding migration using AE2's own transfer pattern. */
public final class TransferEngine {
    private TransferEngine() {}

    public static TransferResult moveWholeUnit(
            MEStorage source,
            MEStorage target,
            AEKey key,
            long amount,
            IActionSource actionSource) {
        return moveWholeUnit(source, target, key, amount, actionSource,
                (insertAmount, mode) -> target.insert(key, insertAmount, mode, actionSource));
    }

    public static TransferResult moveWholeUnitPowered(
            MEStorage source,
            MEStorage target,
            AEKey key,
            long amount,
            IActionSource actionSource,
            IEnergySource energySource) {
        return moveWholeUnit(source, target, key, amount, actionSource,
                (insertAmount, mode) -> StorageHelper.poweredInsert(
                        energySource, target, key, insertAmount, actionSource, mode));
    }

    private static TransferResult moveWholeUnit(
            MEStorage source,
            MEStorage target,
            AEKey key,
            long amount,
            IActionSource actionSource,
            TargetInsert targetInsert) {
        if (amount <= 0 || source == target) {
            return TransferResult.rejected();
        }

        var accepted = targetInsert.insert(amount, Actionable.SIMULATE);
        if (accepted != amount) {
            return TransferResult.rejected();
        }

        var extractable = source.extract(key, amount, Actionable.SIMULATE, actionSource);
        if (extractable != amount) {
            return TransferResult.rejected();
        }

        var extracted = source.extract(key, amount, Actionable.MODULATE, actionSource);
        if (extracted <= 0) {
            return TransferResult.rejected();
        }

        var inserted = targetInsert.insert(extracted, Actionable.MODULATE);
        var remainder = extracted - inserted;
        if (remainder == 0) {
            return new TransferResult(inserted, 0, TransferStatus.MOVED);
        }

        var restored = source.insert(key, remainder, Actionable.MODULATE, actionSource);
        var status = restored == remainder ? TransferStatus.PARTIAL_ROLLED_BACK : TransferStatus.ROLLBACK_FAILED;
        return new TransferResult(inserted, restored, status);
    }

    @FunctionalInterface
    private interface TargetInsert {
        long insert(long amount, Actionable mode);
    }
}

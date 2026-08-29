package dev.yuzhe.aeaffinity.transfer;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

/** Performs one complete, synchronous, non-yielding migration using AE2's own transfer pattern. */
public final class TransferEngine {
    private TransferEngine() {}

    public static TransferResult moveWholeUnit(
            MEStorage source,
            MEStorage target,
            AEKey key,
            long amount,
            IActionSource actionSource) {
        if (amount <= 0 || source == target) {
            return TransferResult.rejected();
        }

        var accepted = target.insert(key, amount, Actionable.SIMULATE, actionSource);
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

        var inserted = target.insert(key, extracted, Actionable.MODULATE, actionSource);
        var remainder = extracted - inserted;
        if (remainder == 0) {
            return new TransferResult(inserted, 0, TransferStatus.MOVED);
        }

        var restored = source.insert(key, remainder, Actionable.MODULATE, actionSource);
        var status = restored == remainder ? TransferStatus.PARTIAL_ROLLED_BACK : TransferStatus.ROLLBACK_FAILED;
        return new TransferResult(inserted, restored, status);
    }
}

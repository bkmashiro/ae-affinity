package dev.yuzhe.aeaffinity.endpoint;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Bounded slot-level fallback for external inventories that cannot push change notifications. */
final class SlotFingerprint {
    static final int MAX_SLOTS = 4096;

    private IItemHandler handler;
    private int[] fingerprints = new int[0];
    private boolean[] observed = new boolean[0];
    private int cursor;

    boolean scan(@Nullable IItemHandler current, int budget) {
        if (budget <= 0 || current == null || current.getSlots() > MAX_SLOTS) {
            reset(current);
            return false;
        }
        if (handler == null || current.getSlots() != fingerprints.length) {
            reset(current);
        } else {
            handler = current;
        }
        if (fingerprints.length == 0) {
            return false;
        }

        boolean changed = false;
        int checked = Math.min(budget, fingerprints.length);
        for (int i = 0; i < checked; i++) {
            int slot = cursor++;
            if (cursor >= fingerprints.length) {
                cursor = 0;
            }
            int fingerprint = fingerprint(current.getStackInSlot(slot));
            if (observed[slot] && fingerprints[slot] != fingerprint) {
                changed = true;
            }
            fingerprints[slot] = fingerprint;
            observed[slot] = true;
        }
        return changed;
    }

    private void reset(@Nullable IItemHandler current) {
        handler = current;
        int slots = current == null || current.getSlots() > MAX_SLOTS ? 0 : current.getSlots();
        fingerprints = new int[slots];
        observed = new boolean[slots];
        cursor = 0;
    }

    private static int fingerprint(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return 31 * ItemStack.hashItemAndComponents(stack) + stack.getCount();
    }
}

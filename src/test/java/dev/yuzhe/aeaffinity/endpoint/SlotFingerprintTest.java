package dev.yuzhe.aeaffinity.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

class SlotFingerprintTest {
    @Test
    void establishesBaselineThenDetectsExternalCountChange() {
        var handler = new ItemStackHandler(3);
        handler.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 32));
        var fingerprint = new SlotFingerprint();

        assertThat(fingerprint.scan(handler, 3)).isFalse();
        handler.setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));

        assertThat(fingerprint.scan(handler, 3)).isTrue();
    }

    @Test
    void scanBudgetLimitsHandlerReads() {
        var handler = new CountingHandler(20);
        var fingerprint = new SlotFingerprint();

        fingerprint.scan(handler, 4);

        assertThat(handler.reads).isEqualTo(4);
    }

    @Test
    void sameSizedHandlerReplacementIsDetectedAsAChange() {
        var first = new ItemStackHandler(1);
        first.setStackInSlot(0, new ItemStack(Items.DIRT));
        var second = new ItemStackHandler(1);
        second.setStackInSlot(0, new ItemStack(Items.DIAMOND));
        var fingerprint = new SlotFingerprint();
        fingerprint.scan(first, 1);

        assertThat(fingerprint.scan(second, 1)).isTrue();
    }

    @Test
    void oversizedHandlersAreSkipped() {
        var handler = new CountingHandler(SlotFingerprint.MAX_SLOTS + 1);
        var fingerprint = new SlotFingerprint();

        assertThat(fingerprint.scan(handler, 8)).isFalse();
        assertThat(handler.reads).isZero();
    }

    private static final class CountingHandler extends ItemStackHandler {
        private int reads;

        private CountingHandler(int size) {
            super(size);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            reads++;
            return super.getStackInSlot(slot);
        }
    }
}

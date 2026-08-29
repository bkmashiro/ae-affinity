package dev.yuzhe.aeaffinity.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class TransferEngineTest {
    private final AEKey key = mock(AEKey.class);
    private final IActionSource actionSource = mock(IActionSource.class);

    @Test
    void movesOnlyWhenTheWholeOptimizationUnitFits() {
        var source = new FakeStorage(Map.of(key, 3L), Long.MAX_VALUE);
        var target = new FakeStorage(Map.of(), 3);

        var result = TransferEngine.moveWholeUnit(source, target, key, 3, actionSource);

        assertThat(result).isEqualTo(new TransferResult(3, 0, TransferStatus.MOVED));
        assertThat(source.amount(key)).isZero();
        assertThat(target.amount(key)).isEqualTo(3);
    }

    @Test
    void poweredMoveChargesAeEnergy() {
        var itemKey = AEItemKey.of(Items.COBBLESTONE);
        var source = new FakeStorage(Map.of(itemKey, 3L), Long.MAX_VALUE);
        var target = new FakeStorage(Map.of(), 3);
        var energy = mock(IEnergySource.class);
        Mockito.when(energy.extractAEPower(
                        ArgumentMatchers.anyDouble(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenAnswer(call -> call.getArgument(0));

        var result = TransferEngine.moveWholeUnitPowered(
                source, target, itemKey, 3, actionSource, energy);

        assertThat(result.status()).isEqualTo(TransferStatus.MOVED);
        Mockito.verify(energy).extractAEPower(
                ArgumentMatchers.anyDouble(),
                ArgumentMatchers.eq(Actionable.MODULATE),
                ArgumentMatchers.any());
    }

    @Test
    void doesNotExtractWhenTargetCannotAcceptTheWholeUnit() {
        var source = new FakeStorage(Map.of(key, 3L), Long.MAX_VALUE);
        var target = new FakeStorage(Map.of(), 2);

        var result = TransferEngine.moveWholeUnit(source, target, key, 3, actionSource);

        assertThat(result.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(source.amount(key)).isEqualTo(3);
        assertThat(target.amount(key)).isZero();
    }

    @Test
    void immediatelyReturnsAnUnexpectedRemainderToTheSource() {
        var source = new FakeStorage(Map.of(key, 3L), Long.MAX_VALUE);
        var target = new FakeStorage(Map.of(), 3);
        target.actualInsertLimit = 1;

        var result = TransferEngine.moveWholeUnit(source, target, key, 3, actionSource);

        assertThat(result).isEqualTo(new TransferResult(1, 2, TransferStatus.PARTIAL_ROLLED_BACK));
        assertThat(source.amount(key)).isEqualTo(2);
        assertThat(target.amount(key)).isEqualTo(1);
    }

    private static final class FakeStorage implements MEStorage {
        private final Map<AEKey, Long> amounts = new HashMap<>();
        private final long capacity;
        private long actualInsertLimit = Long.MAX_VALUE;

        private FakeStorage(Map<AEKey, Long> initial, long capacity) {
            amounts.putAll(initial);
            this.capacity = capacity;
        }

        long amount(AEKey key) {
            return amounts.getOrDefault(key, 0L);
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return net.minecraft.network.chat.Component.literal("fake");
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            var free = capacity - amounts.values().stream().mapToLong(Long::longValue).sum();
            var accepted = Math.min(amount, free);
            if (mode == Actionable.MODULATE) {
                accepted = Math.min(accepted, actualInsertLimit);
                amounts.merge(what, accepted, Long::sum);
            }
            return accepted;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            var extracted = Math.min(amount, amount(what));
            if (mode == Actionable.MODULATE) {
                amounts.compute(what, (ignored, current) -> current == null || current == extracted ? null : current - extracted);
            }
            return extracted;
        }
    }
}

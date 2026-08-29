package dev.yuzhe.aeaffinity.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.List;
import java.util.Random;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class EndpointPlacementIndexTest {
    private static final List<AEItemKey> KEYS = List.of(
            AEItemKey.of(Items.COBBLESTONE),
            AEItemKey.of(Items.DIRT),
            AEItemKey.of(Items.SAND),
            AEItemKey.of(Items.GRAVEL),
            AEItemKey.of(Items.CLAY));

    @Test
    void refreshesOnlyTheConfiguredNumberOfKeysPerStep() {
        var storage = storageWith(KEYS);
        var endpoint = endpoint(storage);
        var index = new EndpointPlacementIndex(new Random(1));
        index.mount(endpoint);

        assertThat(index.refreshOne(2, (ignored, key, amount) -> true)).isTrue();
        assertThat(index.revision(storage)).isZero();
        assertThat(index.candidateCount(storage)).isEqualTo(2);
        assertThat(index.pendingCount()).isEqualTo(1);

        index.refreshOne(2, (ignored, key, amount) -> true);
        assertThat(index.revision(storage)).isZero();
        assertThat(index.candidateCount(storage)).isEqualTo(4);

        index.refreshOne(2, (ignored, key, amount) -> true);
        assertThat(index.revision(storage)).isEqualTo(1);
        assertThat(index.candidateCount(storage)).isEqualTo(5);
        assertThat(index.pendingCount()).isZero();
    }

    @Test
    void dirtyRefreshReplacesHintsAndAdvancesRevision() {
        var storage = storageWith(KEYS.subList(0, 2));
        var endpoint = endpoint(storage);
        var index = new EndpointPlacementIndex(new Random(2));
        index.mount(endpoint);
        index.refreshOne(8, (ignored, key, amount) -> true);

        assertThat(index.sampleSource()).isNotNull();
        assertThat(index.revision(storage)).isEqualTo(1);

        index.markDirty(endpoint);
        index.refreshOne(8, (ignored, key, amount) -> false);

        assertThat(index.revision(storage)).isEqualTo(2);
        assertThat(index.candidateCount(storage)).isZero();
        assertThat(index.sampleSource()).isNull();
    }

    @Test
    void stableEndpointsAreReconciledRoundRobin() {
        var first = storageWith(KEYS.subList(0, 1));
        var second = storageWith(KEYS.subList(1, 2));
        var index = new EndpointPlacementIndex(new Random(3));
        index.mount(endpoint(first));
        index.mount(endpoint(second));

        index.refreshOne(8, (ignored, key, amount) -> true);
        index.refreshOne(8, (ignored, key, amount) -> true);
        assertThat(index.revision(first) + index.revision(second)).isEqualTo(2);

        index.refreshOne(8, (ignored, key, amount) -> true);
        assertThat(index.revision(first) + index.revision(second)).isEqualTo(3);
    }

    @Test
    void candidateReservoirStaysBoundedOnLargeVariantSets() {
        var variants = new java.util.ArrayList<AEItemKey>();
        for (int damage = 0; damage < 40; damage++) {
            var stack = new net.minecraft.world.item.ItemStack(Items.IRON_SWORD);
            stack.setDamageValue(damage);
            variants.add(AEItemKey.of(stack));
        }
        var storage = storageWith(variants);
        var index = new EndpointPlacementIndex(new Random(5));
        index.mount(endpoint(storage));

        index.refreshOne(100, (ignored, key, amount) -> true);

        assertThat(index.revision(storage)).isEqualTo(1);
        assertThat(index.candidateCount(storage)).isEqualTo(16);
    }

    @Test
    void targetSamplingIsBoundedAndNeverReturnsTheSource() {
        var index = new EndpointPlacementIndex(new Random(6));
        var source = endpoint(storageWith(KEYS.subList(0, 1)));
        index.mount(source);
        for (int i = 1; i < KEYS.size(); i++) {
            index.mount(endpoint(storageWith(KEYS.subList(i, i + 1))));
        }

        var targets = index.sampleTargets(source, 3, new Random(7));

        assertThat(targets).hasSize(3).doesNotContain(source).doesNotHaveDuplicates();
    }

    @Test
    void providerChangeDirtiesEveryMountedStorageFromThatProvider() {
        var provider = mock(IStorageProvider.class);
        var first = storageWith(KEYS.subList(0, 1));
        var second = storageWith(KEYS.subList(1, 2));
        var index = new EndpointPlacementIndex(new Random(8));
        index.mount(endpoint(provider, first));
        index.mount(endpoint(provider, second));
        index.refreshOne(8, (ignored, key, amount) -> true);
        index.refreshOne(8, (ignored, key, amount) -> true);
        assertThat(index.pendingCount()).isZero();

        index.markDirty(provider);

        assertThat(index.pendingCount()).isEqualTo(2);
    }

    @Test
    void unmountedEndpointsLeaveNoQueuedRefresh() {
        var storage = storageWith(KEYS);
        var endpoint = endpoint(storage);
        var index = new EndpointPlacementIndex(new Random(4));
        index.mount(endpoint);

        index.unmount(endpoint);

        assertThat(index.size()).isZero();
        assertThat(index.pendingCount()).isZero();
        assertThat(index.refreshOne(8, (ignored, key, amount) -> true)).isFalse();
    }

    private static MountedEndpoint endpoint(MEStorage storage) {
        return endpoint(mock(IStorageProvider.class), storage);
    }

    private static MountedEndpoint endpoint(IStorageProvider provider, MEStorage storage) {
        return new MountedEndpoint(provider, storage, 0);
    }

    private static MEStorage storageWith(List<AEItemKey> keys) {
        var counter = new KeyCounter();
        keys.forEach(key -> counter.add(key, 1));
        return new FakeStorage(counter);
    }

    private record FakeStorage(KeyCounter stacks) implements MEStorage {
        @Override
        public Component getDescription() {
            return Component.literal("fake");
        }

        @Override
        public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0;
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            return 0;
        }

        @Override
        public KeyCounter getAvailableStacks() {
            return stacks;
        }
    }
}

package dev.yuzhe.aeaffinity.endpoint;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Bounded, incrementally refreshed placement hints. Every move is still revalidated at commit time. */
public final class EndpointPlacementIndex {
    private static final int MAX_CANDIDATES_PER_ENDPOINT = 16;

    private final Map<MEStorage, Snapshot> snapshots = new IdentityHashMap<>();
    private final ArrayDeque<MEStorage> dirtyQueue = new ArrayDeque<>();
    private final Set<MEStorage> queued = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<IStorageProvider, Set<MEStorage>> providerStorages = new IdentityHashMap<>();
    private final List<MEStorage> reconciliationOrder = new ArrayList<>();
    private final List<MEStorage> candidateSources = new ArrayList<>();
    private final Set<MEStorage> candidateSourceSet = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Random random;
    private int reconcileCursor;

    public EndpointPlacementIndex() {
        this(new Random());
    }

    EndpointPlacementIndex(Random random) {
        this.random = random;
    }

    public void mount(MountedEndpoint endpoint) {
        var previous = snapshots.put(endpoint.storage(), new Snapshot(endpoint));
        if (previous != null) {
            removeProviderStorage(previous.endpoint.provider(), endpoint.storage());
        }
        if (previous == null) {
            reconciliationOrder.add(endpoint.storage());
        }
        removeCandidateSource(endpoint.storage());
        providerStorages.computeIfAbsent(
                endpoint.provider(), ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(endpoint.storage());
        enqueue(endpoint.storage());
    }

    public void unmount(MountedEndpoint endpoint) {
        var removed = snapshots.remove(endpoint.storage());
        if (removed != null) {
            removeProviderStorage(removed.endpoint.provider(), endpoint.storage());
        }
        queued.remove(endpoint.storage());
        dirtyQueue.removeIf(storage -> storage == endpoint.storage());
        reconciliationOrder.removeIf(storage -> storage == endpoint.storage());
        removeCandidateSource(endpoint.storage());
    }

    public void markDirty(MountedEndpoint endpoint) {
        var snapshot = snapshots.get(endpoint.storage());
        if (snapshot != null && snapshot.endpoint == endpoint) {
            snapshot.cursor = null;
            enqueue(endpoint.storage());
        }
    }

    public void markDirty(IStorageProvider provider) {
        var storages = providerStorages.get(provider);
        if (storages == null) {
            return;
        }
        for (var storage : storages) {
            var snapshot = snapshots.get(storage);
            if (snapshot != null) {
                snapshot.cursor = null;
                enqueue(storage);
            }
        }
    }

    public boolean hasProvider(IStorageProvider provider) {
        return providerStorages.containsKey(provider);
    }

    public List<MountedEndpoint> endpoints(IStorageProvider provider) {
        var storages = providerStorages.get(provider);
        if (storages == null || storages.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<MountedEndpoint>(storages.size());
        for (var storage : storages) {
            var snapshot = snapshots.get(storage);
            if (snapshot != null) {
                result.add(snapshot.endpoint);
            }
        }
        return result;
    }

    public boolean contains(MountedEndpoint endpoint) {
        var snapshot = snapshots.get(endpoint.storage());
        return snapshot != null && snapshot.endpoint == endpoint;
    }

    public int size() {
        return snapshots.size();
    }

    public List<MountedEndpoint> sampleTargets(MountedEndpoint source, int limit, Random picker) {
        if (limit <= 0 || reconciliationOrder.size() <= 1) {
            return List.of();
        }
        var result = new ArrayList<MountedEndpoint>(Math.min(limit, reconciliationOrder.size() - 1));
        var start = picker.nextInt(reconciliationOrder.size());
        for (int offset = 0; offset < reconciliationOrder.size() && result.size() < limit; offset++) {
            var storage = reconciliationOrder.get((start + offset) % reconciliationOrder.size());
            var snapshot = snapshots.get(storage);
            if (snapshot != null && snapshot.endpoint != source) {
                result.add(snapshot.endpoint);
            }
        }
        return result;
    }

    /**
     * Advances one dirty endpoint by at most {@code budget} observed keys. If nothing is dirty,
     * one stable endpoint is selected round-robin for low-frequency reconciliation.
     */
    public boolean refreshOne(int budget, UsefulPlacementFilter filter) {
        if (budget <= 0 || snapshots.isEmpty()) {
            return false;
        }
        if (dirtyQueue.isEmpty()) {
            if (reconcileCursor >= reconciliationOrder.size()) {
                reconcileCursor = 0;
            }
            var storage = reconciliationOrder.get(reconcileCursor++);
            snapshots.get(storage).cursor = null;
            enqueue(storage);
        }

        var storage = dirtyQueue.removeFirst();
        queued.remove(storage);
        var snapshot = snapshots.get(storage);
        if (snapshot == null) {
            return false;
        }
        if (snapshot.cursor == null) {
            snapshot.beginRefresh();
        }

        int visited = 0;
        while (visited < budget && snapshot.cursor.hasNext()) {
            var entry = snapshot.cursor.next();
            visited++;
            if (entry.getKey() instanceof AEItemKey itemKey
                    && entry.getLongValue() > 0
                    && filter.isUseful(snapshot.endpoint, itemKey, entry.getLongValue())) {
                snapshot.offer(new Placement(itemKey, entry.getLongValue()), random);
            }
        }

        if (snapshot.cursor.hasNext()) {
            enqueue(storage);
        } else {
            snapshot.cursor = null;
            snapshot.revision++;
        }
        updateCandidateSource(storage, snapshot);
        return true;
    }

    public IndexedPlacement sampleSource() {
        if (candidateSources.isEmpty()) {
            return null;
        }
        var storage = candidateSources.get(random.nextInt(candidateSources.size()));
        var selected = snapshots.get(storage);
        if (selected == null || selected.candidates.isEmpty()) {
            removeCandidateSource(storage);
            return null;
        }
        var placement = selected.candidates.get(random.nextInt(selected.candidates.size()));
        return new IndexedPlacement(selected.endpoint, placement.key, placement.amount);
    }

    long revision(MEStorage storage) {
        var snapshot = snapshots.get(storage);
        return snapshot == null ? -1 : snapshot.revision;
    }

    int candidateCount(MEStorage storage) {
        var snapshot = snapshots.get(storage);
        return snapshot == null ? 0 : snapshot.candidates.size();
    }

    int pendingCount() {
        return dirtyQueue.size();
    }

    private void enqueue(MEStorage storage) {
        if (queued.add(storage)) {
            dirtyQueue.addLast(storage);
        }
    }

    private void updateCandidateSource(MEStorage storage, Snapshot snapshot) {
        if (snapshot.candidates.isEmpty()) {
            removeCandidateSource(storage);
        } else if (candidateSourceSet.add(storage)) {
            candidateSources.add(storage);
        }
    }

    private void removeCandidateSource(MEStorage storage) {
        if (candidateSourceSet.remove(storage)) {
            candidateSources.removeIf(candidate -> candidate == storage);
        }
    }

    private void removeProviderStorage(IStorageProvider provider, MEStorage storage) {
        var storages = providerStorages.get(provider);
        if (storages != null) {
            storages.remove(storage);
            if (storages.isEmpty()) {
                providerStorages.remove(provider);
            }
        }
    }

    @FunctionalInterface
    public interface UsefulPlacementFilter {
        boolean isUseful(MountedEndpoint endpoint, AEItemKey key, long amount);
    }

    public record IndexedPlacement(MountedEndpoint endpoint, AEItemKey key, long amount) {
    }

    private record Placement(AEItemKey key, long amount) {
    }

    private static final class Snapshot {
        private final MountedEndpoint endpoint;
        private final List<Placement> candidates = new ArrayList<>();
        private Iterator<Object2LongMap.Entry<AEKey>> cursor;
        private int usefulSeen;
        private long revision;

        private Snapshot(MountedEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        private void beginRefresh() {
            candidates.clear();
            usefulSeen = 0;
            cursor = endpoint.storage().getAvailableStacks().iterator();
        }

        private void offer(Placement placement, Random random) {
            usefulSeen++;
            if (candidates.size() < MAX_CANDIDATES_PER_ENDPOINT) {
                candidates.add(placement);
            } else {
                var replacement = random.nextInt(usefulSeen);
                if (replacement < candidates.size()) {
                    candidates.set(replacement, placement);
                }
            }
        }
    }
}

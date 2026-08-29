package dev.yuzhe.aeaffinity.grid;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridServiceProvider;
import appeng.me.helpers.BaseActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.IStorageProvider;
import dev.yuzhe.aeaffinity.affinity.AffinityScorer;
import dev.yuzhe.aeaffinity.affinity.AggregateTargetReport;
import dev.yuzhe.aeaffinity.affinity.EndpointClassifier;
import dev.yuzhe.aeaffinity.affinity.EndpointKind;
import dev.yuzhe.aeaffinity.affinity.MigrationPlan;
import dev.yuzhe.aeaffinity.config.AeAffinityConfig;
import dev.yuzhe.aeaffinity.endpoint.EndpointPlacementIndex;
import dev.yuzhe.aeaffinity.endpoint.ExternalInventoryProbeIndex;
import dev.yuzhe.aeaffinity.endpoint.EndpointListener;
import dev.yuzhe.aeaffinity.endpoint.MountedEndpoint;
import dev.yuzhe.aeaffinity.endpoint.StorageMountObserver;
import dev.yuzhe.aeaffinity.scheduler.CommitResult;
import dev.yuzhe.aeaffinity.scheduler.LazyScheduler;
import dev.yuzhe.aeaffinity.scheduler.MoveCandidate;
import dev.yuzhe.aeaffinity.scheduler.SchedulerLimits;
import dev.yuzhe.aeaffinity.transfer.TransferEngine;
import dev.yuzhe.aeaffinity.transfer.TransferStatus;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One lazy, headless scheduler per AE grid. */
public final class AffinityGridService implements IAffinityGridService, IGridServiceProvider, EndpointListener {
    private static final Logger LOG = LoggerFactory.getLogger(AffinityGridService.class);
    private static final String ANCHOR_TAG = "aeaffinity:anchor";
    private static final int TARGET_SAMPLES = 4;
    private static final int PLACEMENT_SCAN_BUDGET = 8;
    private static final int EXTERNAL_PROBE_INTERVAL_TICKS = 20;
    private static final int EXTERNAL_SLOT_BUDGET = 8;
    private static final int MIN_GAIN = 20;
    private static final long MAX_MOVE = 256;

    private final IGrid grid;
    private final EndpointPlacementIndex endpointIndex = new EndpointPlacementIndex();
    private final AggregateTargetReport aggregateTargetReport = new AggregateTargetReport();
    private final ExternalInventoryProbeIndex externalProbes = new ExternalInventoryProbeIndex();
    private final Set<IGridNode> anchors = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Long, MigrationPlan> plans = new java.util.HashMap<>();
    private final Random random = new Random();
    private final LazyScheduler scheduler;
    private long nextPlanId;
    private int externalProbeTicks;

    public AffinityGridService(IGrid grid, IStorageService storageService) {
        this.grid = grid;
        var limits = new SchedulerLimits(
                20,
                AeAffinityConfig.PLANNING_TICKS.get(),
                AeAffinityConfig.MIN_IDLE_TICKS.get(),
                AeAffinityConfig.MAX_IDLE_TICKS.get());
        this.scheduler = new LazyScheduler(limits, ignored -> plan(), this::commit);
        StorageMountObserver.bind(storageService, this);
    }

    @Override
    public void onServerEndTick() {
        if (isEnabled() && grid.getEnergyService().isNetworkPowered()) {
            if (++externalProbeTicks >= EXTERNAL_PROBE_INTERVAL_TICKS) {
                externalProbeTicks = 0;
                var changed = externalProbes.probeOne(EXTERNAL_SLOT_BUDGET);
                if (changed != null) {
                    onChanged(changed);
                }
            }
            scheduler.tick();
        }
    }

    @Override
    public void addNode(IGridNode node, CompoundTag savedData) {
        if (savedData != null && savedData.getBoolean(ANCHOR_TAG)) {
            anchors.add(node);
        }
    }

    @Override
    public void removeNode(IGridNode node) {
        anchors.remove(node);
    }

    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        if (anchors.contains(node)) {
            savedData.putBoolean(ANCHOR_TAG, true);
        }
    }

    @Override
    public void onMounted(MountedEndpoint endpoint) {
        endpointIndex.mount(endpoint);
        updateTargetReport(endpoint);
        externalProbes.mount(endpoint.provider());
        scheduler.wakeUp();
    }

    @Override
    public void onUnmounted(MountedEndpoint endpoint) {
        endpointIndex.unmount(endpoint);
        aggregateTargetReport.remove(endpoint.storage());
        if (!endpointIndex.hasProvider(endpoint.provider())) {
            externalProbes.unmount(endpoint.provider());
        }
        plans.entrySet().removeIf(entry -> entry.getValue().source().storage() == endpoint.storage()
                || entry.getValue().target().storage() == endpoint.storage());
        scheduler.wakeUp();
    }

    @Override
    public void onChanged(IStorageProvider provider) {
        endpointIndex.markDirty(provider);
        for (var endpoint : endpointIndex.endpoints(provider)) {
            updateTargetReport(endpoint);
        }
        scheduler.wakeUp();
    }

    @Override
    public int mountedEndpointCount() {
        return endpointIndex.size();
    }

    @Override
    public boolean hasAnchor() {
        return !anchors.isEmpty();
    }

    @Override
    public void addAnchor(IGridNode node) {
        if (node.getGrid() != grid) {
            throw new IllegalArgumentException("Anchor node is not part of this grid");
        }
        anchors.add(node);
        scheduler.wakeUp();
    }

    @Override
    public void removeAnchor(IGridNode node) {
        anchors.remove(node);
    }

    @Override
    public int quoteTargetAffinity(AEItemKey key, long amount) {
        return aggregateTargetReport.quote(key.getMaxStackSize(), amount);
    }

    private boolean isEnabled() {
        return switch (AeAffinityConfig.ACTIVATION.get()) {
            case OFF -> false;
            case ANCHORED -> hasAnchor();
            case ALL -> true;
        };
    }

    private void plan() {
        if (endpointIndex.size() < 2) {
            return;
        }

        endpointIndex.refreshOne(PLACEMENT_SCAN_BUDGET, this::isUsefulPlacement);
        var placement = endpointIndex.sampleSource();
        if (placement == null) {
            return;
        }

        var source = placement.endpoint();
        var sourceKind = EndpointClassifier.kind(source);
        if (sourceKind == EndpointKind.OPAQUE || !EndpointClassifier.isRollbackSafeSource(source)) {
            endpointIndex.markDirty(source);
            return;
        }

        var key = placement.key();
        var sourceAmount = placement.amount();
        var moveAmount = moveAmount(sourceKind, key, sourceAmount);
        if (moveAmount == 0) {
            return;
        }

        var sourceAffinity = AffinityScorer.score(sourceKind, key.getMaxStackSize(), sourceAmount);
        for (var target : endpointIndex.sampleTargets(source, TARGET_SAMPLES, random)) {
            if (!EndpointClassifier.isConservativeTarget(target)) {
                continue;
            }

            var targetAffinity = targetAffinity(target, key, sourceAmount);
            if (targetAffinity == AffinityScorer.UNKNOWN) {
                continue;
            }

            var gain = targetAffinity - sourceAffinity;
            if (gain <= MIN_GAIN) {
                continue;
            }

            var id = nextPlanId++;
            plans.put(id, new MigrationPlan(
                    id, source, target, key, moveAmount, gain, new BaseActionSource()));
            scheduler.submit(new MoveCandidate(Long.toString(id), gain));
        }
    }

    private boolean isUsefulPlacement(MountedEndpoint endpoint, AEItemKey key, long amount) {
        var kind = EndpointClassifier.kind(endpoint);
        return kind != EndpointKind.OPAQUE
                && EndpointClassifier.isRollbackSafeSource(endpoint)
                && moveAmount(kind, key, amount) > 0;
    }

    private static long moveAmount(EndpointKind sourceKind, AEItemKey key, long amount) {
        if (sourceKind == EndpointKind.CELL) {
            return key.getMaxStackSize() == 1 && amount <= 4 ? amount : 0;
        }
        if (sourceKind == EndpointKind.SLOTTED) {
            if (key.getMaxStackSize() == 1 && amount >= 16) {
                return Math.min(amount, MAX_MOVE);
            }
            if (key.getMaxStackSize() > 1 && amount >= (long) key.getMaxStackSize() * 4) {
                return Math.min(amount, MAX_MOVE);
            }
        }
        return 0;
    }

    private CommitResult commit(MoveCandidate candidate) {
        var plan = plans.get(Long.parseLong(candidate.id()));
        try {
            if (plan == null || !stillMounted(plan.source()) || !stillMounted(plan.target())) {
                return CommitResult.STALE;
            }
            if (!EndpointClassifier.isRollbackSafeSource(plan.source())
                    || !EndpointClassifier.isConservativeTarget(plan.target())) {
                return CommitResult.REJECTED;
            }

            var sourceKind = EndpointClassifier.kind(plan.source());
            var currentAmount = plan.source().storage().getAvailableStacks().get(plan.key());
            if (currentAmount < plan.amount()) {
                return CommitResult.STALE;
            }
            if (sourceKind == EndpointKind.CELL && currentAmount != plan.amount()) {
                return CommitResult.STALE;
            }

            var currentTargetAffinity = targetAffinity(plan.target(), plan.key(), currentAmount);
            if (currentTargetAffinity == AffinityScorer.UNKNOWN) {
                return CommitResult.REJECTED;
            }
            var currentGain = currentTargetAffinity
                    - AffinityScorer.score(sourceKind, plan.key().getMaxStackSize(), currentAmount);
            if (currentGain <= MIN_GAIN) {
                return CommitResult.REJECTED;
            }

            var result = AeAffinityConfig.CHARGE_ENERGY.get()
                    ? TransferEngine.moveWholeUnitPowered(
                            plan.source().storage(),
                            plan.target().storage(),
                            plan.key(),
                            plan.amount(),
                            plan.actionSource(),
                            grid.getEnergyService())
                    : TransferEngine.moveWholeUnit(
                            plan.source().storage(),
                            plan.target().storage(),
                            plan.key(),
                            plan.amount(),
                            plan.actionSource());
            if (result.status() == TransferStatus.ROLLBACK_FAILED) {
                LOG.error("AE Affinity could not return {} of {} to its source; disabling this candidate path",
                        plan.amount() - result.moved() - result.restored(), plan.key());
                endpointIndex.unmount(plan.source());
                endpointIndex.unmount(plan.target());
                return CommitResult.REJECTED;
            }
            return result.moved() > 0 ? CommitResult.MOVED : CommitResult.STALE;
        } finally {
            if (plan != null) {
                endpointIndex.markDirty(plan.source());
                endpointIndex.markDirty(plan.target());
            }
            plans.clear();
        }
    }

    private boolean stillMounted(MountedEndpoint endpoint) {
        return endpointIndex.contains(endpoint);
    }

    private void updateTargetReport(MountedEndpoint endpoint) {
        aggregateTargetReport.update(
                endpoint.storage(),
                EndpointClassifier.kind(endpoint),
                EndpointClassifier.isConservativeTarget(endpoint));
    }

    private int targetAffinity(MountedEndpoint endpoint, AEItemKey key, long amount) {
        var kind = EndpointClassifier.kind(endpoint);
        if (kind != EndpointKind.AGGREGATE) {
            return AffinityScorer.score(kind, key.getMaxStackSize(), amount);
        }

        var node = EndpointClassifier.nestedGridNode(endpoint);
        if (node == null || node.getGrid() == grid) {
            return AffinityScorer.UNKNOWN;
        }
        return node.getGrid().getService(IAffinityGridService.class).quoteTargetAffinity(key, amount);
    }
}

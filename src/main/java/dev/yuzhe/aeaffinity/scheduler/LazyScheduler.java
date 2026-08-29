package dev.yuzhe.aeaffinity.scheduler;

import java.util.PriorityQueue;

/**
 * A low-density optimistic scheduler. Planning is read-only; at most one candidate is validated and committed in a
 * dedicated tick. Candidates carry no inventory state and may be discarded freely.
 */
public final class LazyScheduler {
    private enum Phase {
        IDLE,
        PLANNING,
        COMMIT
    }

    private final SchedulerLimits limits;
    private final PlanningStep planningStep;
    private final CommitStep commitStep;
    private final PriorityQueue<MoveCandidate> candidates = new PriorityQueue<>();

    private Phase phase = Phase.IDLE;
    private int idleTicksRemaining;
    private int planningTicksRemaining;
    private int currentIntervalTicks;
    private long schedulerTick;
    private long completedRounds;

    public LazyScheduler(SchedulerLimits limits, PlanningStep planningStep, CommitStep commitStep) {
        this.limits = limits;
        this.planningStep = planningStep;
        this.commitStep = commitStep;
        this.idleTicksRemaining = limits.bootstrapIdleTicks();
        this.currentIntervalTicks = limits.minIdleTicks();
    }

    public void tick() {
        var tick = schedulerTick++;
        switch (phase) {
            case IDLE -> tickIdle();
            case PLANNING -> tickPlanning(tick);
            case COMMIT -> tickCommit();
        }
    }

    public void submit(MoveCandidate candidate) {
        candidates.offer(candidate);
    }

    /** Wakes a backed-off scheduler without allowing a topology callback to commit immediately. */
    public void wakeUp() {
        if (phase == Phase.IDLE) {
            currentIntervalTicks = limits.minIdleTicks();
            idleTicksRemaining = Math.min(idleTicksRemaining, currentIntervalTicks);
        }
    }

    public int currentIntervalTicks() {
        return currentIntervalTicks;
    }

    public long completedRounds() {
        return completedRounds;
    }

    private void tickIdle() {
        if (idleTicksRemaining > 0) {
            idleTicksRemaining--;
            return;
        }
        phase = Phase.PLANNING;
        planningTicksRemaining = limits.planningTicks();
        tickPlanning(schedulerTick - 1);
    }

    private void tickPlanning(long tick) {
        planningStep.plan(tick);
        planningTicksRemaining--;
        if (planningTicksRemaining == 0) {
            phase = Phase.COMMIT;
        }
    }

    private void tickCommit() {
        var candidate = candidates.poll();
        var result = candidate == null ? CommitResult.REJECTED : commitStep.validateAndCommit(candidate);
        candidates.clear();

        if (result == CommitResult.MOVED) {
            currentIntervalTicks = limits.minIdleTicks();
        } else {
            currentIntervalTicks = Math.min(limits.maxIdleTicks(), currentIntervalTicks * 2);
        }

        completedRounds++;
        idleTicksRemaining = currentIntervalTicks;
        phase = Phase.IDLE;
    }
}

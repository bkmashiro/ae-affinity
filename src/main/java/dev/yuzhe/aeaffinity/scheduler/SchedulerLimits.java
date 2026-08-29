package dev.yuzhe.aeaffinity.scheduler;

/** Scheduler timing bounds. The short bootstrap delay makes a newly created grid observable quickly. */
public record SchedulerLimits(
        int bootstrapIdleTicks,
        int planningTicks,
        int minIdleTicks,
        int maxIdleTicks) {
    public SchedulerLimits {
        if (bootstrapIdleTicks < 0 || planningTicks < 1 || minIdleTicks < 1 || maxIdleTicks < minIdleTicks) {
            throw new IllegalArgumentException("Invalid scheduler limits");
        }
    }
}

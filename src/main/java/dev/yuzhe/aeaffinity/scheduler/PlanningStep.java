package dev.yuzhe.aeaffinity.scheduler;

@FunctionalInterface
public interface PlanningStep {
    void plan(long schedulerTick);
}

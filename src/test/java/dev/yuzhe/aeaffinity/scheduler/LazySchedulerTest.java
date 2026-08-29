package dev.yuzhe.aeaffinity.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LazySchedulerTest {
    @Test
    void idlesPlansThenCommitsExactlyOneCandidate() {
        var events = new ArrayList<String>();
        var scheduler = new LazyScheduler(
                new SchedulerLimits(2, 3, 10, 40),
                tick -> events.add("plan:" + tick),
                candidate -> {
                    events.add("commit:" + candidate.id());
                    return CommitResult.MOVED;
                });

        scheduler.submit(new MoveCandidate("first", 20));

        for (int tick = 0; tick < 6; tick++) {
            scheduler.tick();
        }

        assertThat(events).containsExactly("plan:2", "plan:3", "plan:4", "commit:first");
        assertThat(scheduler.currentIntervalTicks()).isEqualTo(10);
    }

    @Test
    void neverCommitsAStaleCandidateAndBacksOffAfterNoMove() {
        var committed = new ArrayList<String>();
        var scheduler = new LazyScheduler(
                new SchedulerLimits(1, 1, 4, 16),
                ignored -> {},
                candidate -> {
                    committed.add(candidate.id());
                    return CommitResult.STALE;
                });

        scheduler.submit(new MoveCandidate("stale", 10));
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        assertThat(committed).containsExactly("stale");
        assertThat(scheduler.currentIntervalTicks()).isEqualTo(8);
    }

    @Test
    void successfulWorkSpeedsUpAndEmptyRoundsExponentiallyBackOff() {
        var outcomes = new ArrayList<>(List.of(CommitResult.MOVED));
        var scheduler = new LazyScheduler(
                new SchedulerLimits(1, 1, 2, 16),
                ignored -> {},
                ignored -> outcomes.removeFirst());

        scheduler.submit(new MoveCandidate("move", 1));
        runRound(scheduler);
        assertThat(scheduler.currentIntervalTicks()).isEqualTo(2);

        runRound(scheduler);
        assertThat(scheduler.currentIntervalTicks()).isEqualTo(4);
        runRound(scheduler);
        assertThat(scheduler.currentIntervalTicks()).isEqualTo(8);
    }

    @Test
    void wakeUpShortensLongIdleWithoutCommittingDuringPlanning() {
        var commits = new ArrayList<String>();
        var scheduler = new LazyScheduler(
                new SchedulerLimits(1, 2, 2, 32),
                ignored -> {},
                candidate -> {
                    commits.add(candidate.id());
                    return CommitResult.MOVED;
                });

        runRound(scheduler); // no candidate, backs off to four idle ticks
        scheduler.submit(new MoveCandidate("urgent", 1));
        scheduler.wakeUp();

        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();
        assertThat(commits).isEmpty();

        scheduler.tick();
        assertThat(commits).containsExactly("urgent");
    }

    private static void runRound(LazyScheduler scheduler) {
        var initialRounds = scheduler.completedRounds();
        while (scheduler.completedRounds() == initialRounds) {
            scheduler.tick();
        }
    }
}

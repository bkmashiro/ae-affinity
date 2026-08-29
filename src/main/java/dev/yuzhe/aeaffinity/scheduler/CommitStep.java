package dev.yuzhe.aeaffinity.scheduler;

@FunctionalInterface
public interface CommitStep {
    CommitResult validateAndCommit(MoveCandidate candidate);
}

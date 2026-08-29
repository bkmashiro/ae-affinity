package dev.yuzhe.aeaffinity.scheduler;

/** A cheap, immutable suggestion. No item has been extracted while a candidate exists. */
public record MoveCandidate(String id, int score) implements Comparable<MoveCandidate> {
    @Override
    public int compareTo(MoveCandidate other) {
        return Integer.compare(other.score, score);
    }
}

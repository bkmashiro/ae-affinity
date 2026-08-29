package dev.yuzhe.aeaffinity.affinity;

/** Small, deliberately opinionated MVP heuristic. Higher values are better placements. */
public final class AffinityScorer {
    public static final int UNKNOWN = Integer.MIN_VALUE;

    private AffinityScorer() {
    }

    public static int score(EndpointKind kind, int maxStackSize, long amount) {
        if (amount <= 0 || maxStackSize <= 0) {
            return UNKNOWN;
        }
        return switch (kind) {
            case CELL -> cellScore(maxStackSize, amount);
            case SLOTTED -> slottedScore(maxStackSize, amount);
            case AGGREGATE, OPAQUE -> UNKNOWN;
        };
    }

    private static int cellScore(int maxStackSize, long amount) {
        if (maxStackSize == 1) {
            return amount >= 16 ? 85 : 10;
        }
        return 90 + (int) Math.min(10, amount / maxStackSize);
    }

    private static int slottedScore(int maxStackSize, long amount) {
        if (maxStackSize == 1) {
            return amount <= 4 ? 100 - (int) amount : Math.max(0, 80 - (int) Math.min(80, amount));
        }
        if (amount < maxStackSize) {
            return 60;
        }
        return Math.max(0, 40 - (int) Math.min(40, amount / maxStackSize));
    }
}

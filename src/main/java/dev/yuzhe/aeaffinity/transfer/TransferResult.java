package dev.yuzhe.aeaffinity.transfer;

/** Actual amounts reported by the two MEStorage implementations. */
public record TransferResult(long moved, long restored, TransferStatus status) {
    public static TransferResult rejected() {
        return new TransferResult(0, 0, TransferStatus.REJECTED);
    }
}

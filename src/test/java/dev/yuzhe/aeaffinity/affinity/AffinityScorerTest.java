package dev.yuzhe.aeaffinity.affinity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AffinityScorerTest {
    @Test
    void sparseUnstackablesPreferSlottedStorage() {
        assertThat(AffinityScorer.score(EndpointKind.SLOTTED, 1, 1))
                .isGreaterThan(AffinityScorer.score(EndpointKind.CELL, 1, 1));
    }

    @Test
    void numerousUnstackablesPreferCells() {
        assertThat(AffinityScorer.score(EndpointKind.CELL, 1, 100))
                .isGreaterThan(AffinityScorer.score(EndpointKind.SLOTTED, 1, 100));
    }

    @Test
    void ordinaryBulkItemsPreferCells() {
        assertThat(AffinityScorer.score(EndpointKind.CELL, 64, 4096))
                .isGreaterThan(AffinityScorer.score(EndpointKind.SLOTTED, 64, 4096));
    }

    @Test
    void opaqueStorageNeverProducesAnAutomaticQuote() {
        assertThat(AffinityScorer.score(EndpointKind.OPAQUE, 1, 1)).isEqualTo(AffinityScorer.UNKNOWN);
    }
}

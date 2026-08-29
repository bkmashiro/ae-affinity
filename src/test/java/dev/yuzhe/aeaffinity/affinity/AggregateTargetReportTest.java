package dev.yuzhe.aeaffinity.affinity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AggregateTargetReportTest {
    @Test
    void homogeneousDirectStorageProducesQuote() {
        var report = new AggregateTargetReport();
        var first = new Object();
        var second = new Object();

        report.update(first, EndpointKind.SLOTTED, true);
        report.update(second, EndpointKind.SLOTTED, true);

        assertThat(report.quote(1, 1)).isEqualTo(99);
    }

    @Test
    void mixedStorageUsesWorstPossiblePlacement() {
        var report = new AggregateTargetReport();
        report.update(new Object(), EndpointKind.CELL, true);
        report.update(new Object(), EndpointKind.SLOTTED, true);

        assertThat(report.quote(1, 1)).isEqualTo(10);
        assertThat(report.quote(64, 256)).isEqualTo(36);
    }

    @Test
    void unknownOrUnsafeRouteSuppressesQuote() {
        var report = new AggregateTargetReport();
        var direct = new Object();
        var unsafe = new Object();
        report.update(direct, EndpointKind.CELL, true);
        report.update(unsafe, EndpointKind.OPAQUE, false);

        assertThat(report.quote(64, 256)).isEqualTo(AffinityScorer.UNKNOWN);

        report.remove(unsafe);
        assertThat(report.quote(64, 256)).isEqualTo(94);
    }

    @Test
    void aggregateRoutesNeverEnterAReport() {
        var report = new AggregateTargetReport();
        report.update(new Object(), EndpointKind.AGGREGATE, true);

        assertThat(report.quote(1, 1)).isEqualTo(AffinityScorer.UNKNOWN);
    }

    @Test
    void updatingAndRemovingEndpointsKeepsCountsExact() {
        var report = new AggregateTargetReport();
        var endpoint = new Object();
        report.update(endpoint, EndpointKind.CELL, true);
        report.update(endpoint, EndpointKind.SLOTTED, true);
        assertThat(report.quote(1, 1)).isEqualTo(99);

        report.remove(endpoint);
        assertThat(report.quote(1, 1)).isEqualTo(AffinityScorer.UNKNOWN);
    }
}

package dev.yuzhe.aeaffinity.affinity;

import java.util.IdentityHashMap;
import java.util.Map;

/** A constant-time conservative summary of the direct insertion routes in one AE grid. */
public final class AggregateTargetReport {
    private final Map<Object, Route> routes = new IdentityHashMap<>();
    private int cellRoutes;
    private int slottedRoutes;
    private int blockers;

    public void update(Object identity, EndpointKind kind, boolean safeTarget) {
        remove(identity);
        var route = route(kind, safeTarget);
        routes.put(identity, route);
        add(route, 1);
    }

    public void remove(Object identity) {
        var previous = routes.remove(identity);
        if (previous != null) {
            add(previous, -1);
        }
    }

    public int quote(int maxStackSize, long amount) {
        if (blockers > 0 || cellRoutes + slottedRoutes == 0) {
            return AffinityScorer.UNKNOWN;
        }

        int result = Integer.MAX_VALUE;
        if (cellRoutes > 0) {
            result = Math.min(result, AffinityScorer.score(EndpointKind.CELL, maxStackSize, amount));
        }
        if (slottedRoutes > 0) {
            result = Math.min(result, AffinityScorer.score(EndpointKind.SLOTTED, maxStackSize, amount));
        }
        return result;
    }

    private static Route route(EndpointKind kind, boolean safeTarget) {
        if (!safeTarget) {
            return Route.BLOCKER;
        }
        return switch (kind) {
            case CELL -> Route.CELL;
            case SLOTTED -> Route.SLOTTED;
            case AGGREGATE, OPAQUE -> Route.BLOCKER;
        };
    }

    private void add(Route route, int delta) {
        switch (route) {
            case CELL -> cellRoutes += delta;
            case SLOTTED -> slottedRoutes += delta;
            case BLOCKER -> blockers += delta;
        }
    }

    private enum Route {
        CELL,
        SLOTTED,
        BLOCKER
    }
}

package dev.yuzhe.aeaffinity.affinity;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import dev.yuzhe.aeaffinity.endpoint.MountedEndpoint;

public record MigrationPlan(
        long id,
        MountedEndpoint source,
        MountedEndpoint target,
        AEItemKey key,
        long amount,
        int expectedGain,
        IActionSource actionSource) {
}

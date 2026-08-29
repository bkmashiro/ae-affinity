package dev.yuzhe.aeaffinity.grid;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.stacks.AEItemKey;

public interface IAffinityGridService extends IGridService {
    int mountedEndpointCount();

    boolean hasAnchor();

    void addAnchor(IGridNode node);

    void removeAnchor(IGridNode node);

    int quoteTargetAffinity(AEItemKey key, long amount);
}

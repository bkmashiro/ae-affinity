package dev.yuzhe.aeaffinity.grid;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;

public interface IAffinityGridService extends IGridService {
    int mountedEndpointCount();

    boolean hasAnchor();

    void addAnchor(IGridNode node);

    void removeAnchor(IGridNode node);
}

package dev.yuzhe.aeaffinity.endpoint;

import appeng.api.storage.IStorageProvider;
import appeng.parts.storagebus.StorageBusPart;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Probes one standard item-handler endpoint at a time with a fixed slot budget. */
public final class ExternalInventoryProbeIndex {
    private final Map<IStorageProvider, SlotFingerprint> probes = new IdentityHashMap<>();
    private final List<IStorageProvider> order = new ArrayList<>();
    private int cursor;

    public void mount(IStorageProvider provider) {
        if (provider instanceof StorageBusPart && !probes.containsKey(provider)) {
            probes.put(provider, new SlotFingerprint());
            order.add(provider);
        }
    }

    public void unmount(IStorageProvider provider) {
        probes.remove(provider);
        order.removeIf(candidate -> candidate == provider);
        if (cursor >= order.size()) {
            cursor = 0;
        }
    }

    @Nullable
    public IStorageProvider probeOne(int slotBudget) {
        if (order.isEmpty()) {
            return null;
        }
        if (cursor >= order.size()) {
            cursor = 0;
        }
        var provider = order.get(cursor++);
        var probe = probes.get(provider);
        if (probe == null || !(provider instanceof StorageBusPart storageBus)) {
            return null;
        }
        return probe.scan(findItemHandler(storageBus), slotBudget) ? provider : null;
    }

    static @Nullable IItemHandler findItemHandler(StorageBusPart storageBus) {
        if (!(storageBus.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        var targetPos = storageBus.getBlockEntity().getBlockPos().relative(storageBus.getSide());
        return level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, storageBus.getSide().getOpposite());
    }
}

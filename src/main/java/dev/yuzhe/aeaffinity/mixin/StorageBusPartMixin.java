package dev.yuzhe.aeaffinity.mixin;

import appeng.parts.storagebus.StorageBusPart;
import dev.yuzhe.aeaffinity.endpoint.StorageMountObserver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StorageBusPart.class)
public abstract class StorageBusPartMixin {
    @Inject(method = "invalidateOnExternalStorageChange", at = @At("HEAD"))
    private void aeaffinity$markEndpointDirty(CallbackInfo callback) {
        var part = (StorageBusPart) (Object) this;
        part.getMainNode().ifPresent((grid, node) ->
                StorageMountObserver.changed(grid.getStorageService(), part));
    }
}

package dev.yuzhe.aeaffinity.mixin;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.service.StorageService;
import dev.yuzhe.aeaffinity.endpoint.StorageMountObserver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.service.StorageService$ProviderState", remap = false)
abstract class ProviderStateMixin {
    @Shadow
    @Final
    private IStorageProvider provider;

    @Shadow
    @Final
    private StorageService this$0;

    @Inject(method = "mount(Lappeng/api/storage/MEStorage;I)V", at = @At("TAIL"))
    private void aeaffinity$mounted(MEStorage storage, int priority, CallbackInfo callback) {
        StorageMountObserver.mounted(this$0, provider, storage, priority);
    }

    @Inject(method = "unmount(Lappeng/api/storage/MEStorage;)V", at = @At("HEAD"))
    private void aeaffinity$unmounted(MEStorage storage, CallbackInfo callback) {
        StorageMountObserver.unmounted(this$0, provider, storage, 0);
    }
}

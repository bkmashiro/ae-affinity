package dev.yuzhe.aeaffinity.endpoint;

import appeng.api.storage.IStorageProvider;

public interface EndpointListener {
    void onMounted(MountedEndpoint endpoint);

    void onUnmounted(MountedEndpoint endpoint);

    void onChanged(IStorageProvider provider);
}

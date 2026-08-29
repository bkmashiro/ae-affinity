package dev.yuzhe.aeaffinity.endpoint;

public interface EndpointListener {
    void onMounted(MountedEndpoint endpoint);

    void onUnmounted(MountedEndpoint endpoint);
}

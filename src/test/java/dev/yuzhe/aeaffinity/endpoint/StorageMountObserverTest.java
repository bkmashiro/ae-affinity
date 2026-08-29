package dev.yuzhe.aeaffinity.endpoint;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageProvider;
import org.junit.jupiter.api.Test;

class StorageMountObserverTest {
    @Test
    void forwardsProviderChangeToBoundGridListener() {
        var service = mock(IStorageService.class);
        var provider = mock(IStorageProvider.class);
        var listener = mock(EndpointListener.class);
        StorageMountObserver.bind(service, listener);

        StorageMountObserver.changed(service, provider);

        verify(listener).onChanged(provider);
    }
}

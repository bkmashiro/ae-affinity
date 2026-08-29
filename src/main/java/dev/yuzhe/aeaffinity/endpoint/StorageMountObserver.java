package dev.yuzhe.aeaffinity.endpoint;

import appeng.api.networking.storage.IStorageService;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Bridge used by the single narrow StorageService mixin. */
public final class StorageMountObserver {
    private static final Map<IStorageService, WeakReference<EndpointListener>> LISTENERS = new WeakHashMap<>();

    private StorageMountObserver() {
    }

    public static synchronized void bind(IStorageService service, EndpointListener listener) {
        LISTENERS.put(service, new WeakReference<>(listener));
    }

    public static synchronized void mounted(
            IStorageService service,
            IStorageProvider provider,
            MEStorage storage,
            int priority) {
        var listener = listener(service);
        if (listener != null) {
            listener.onMounted(new MountedEndpoint(provider, storage, priority));
        }
    }

    public static synchronized void unmounted(
            IStorageService service,
            IStorageProvider provider,
            MEStorage storage,
            int priority) {
        var listener = listener(service);
        if (listener != null) {
            listener.onUnmounted(new MountedEndpoint(provider, storage, priority));
        }
    }

    public static synchronized void changed(IStorageService service, IStorageProvider provider) {
        var listener = listener(service);
        if (listener != null) {
            listener.onChanged(provider);
        }
    }

    private static EndpointListener listener(IStorageService service) {
        var reference = LISTENERS.get(service);
        if (reference == null) {
            return null;
        }
        var listener = reference.get();
        if (listener == null) {
            LISTENERS.remove(service);
        }
        return listener;
    }
}

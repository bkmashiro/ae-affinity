package dev.yuzhe.aeaffinity.endpoint;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;

/** Provenance that AE2 normally discards when flattening mounts into NetworkStorage. */
public record MountedEndpoint(IStorageProvider provider, MEStorage storage, int priority) {
}

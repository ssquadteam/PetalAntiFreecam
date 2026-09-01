package com.boggy.petalantifreecam.refresh;

import java.util.ArrayDeque;
import java.util.Collection;

final class PlayerChunkRefreshQueue {

    private final ArrayDeque<ChunkRefreshKey> refreshKeys;

    PlayerChunkRefreshQueue(Collection<ChunkRefreshKey> refreshKeys) {
        this.refreshKeys = new ArrayDeque<>(refreshKeys);
    }

    ChunkRefreshKey poll() {
        return refreshKeys.remove();
    }

    boolean hasRemaining() {
        return !refreshKeys.isEmpty();
    }
}

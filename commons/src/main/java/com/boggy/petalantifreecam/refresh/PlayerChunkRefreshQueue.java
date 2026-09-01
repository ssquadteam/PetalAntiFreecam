package com.boggy.petalantifreecam.refresh;

import java.util.ArrayDeque;
import java.util.Collection;

final class PlayerChunkRefreshQueue {

    private final int generation;
    private final ArrayDeque<ChunkRefreshKey> refreshKeys;

    PlayerChunkRefreshQueue(int generation, Collection<ChunkRefreshKey> refreshKeys) {
        this.generation = generation;
        this.refreshKeys = new ArrayDeque<>(refreshKeys);
    }

    int generation() {
        return generation;
    }

    ChunkRefreshKey poll() {
        return refreshKeys.remove();
    }

    boolean hasRemaining() {
        return !refreshKeys.isEmpty();
    }
}

package com.boggy.petalantifreecam.refresh;

import java.util.UUID;

public record ChunkRefreshKey(UUID worldId, long chunkKey) {
}

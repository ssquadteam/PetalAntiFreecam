package com.boggy.petalantifreecam.v26_2;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

final class NmsPacketAccess {

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<Heightmap.Types, long[]>> HEIGHTMAPS_CODEC;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Object>> BLOCK_ENTITY_LIST_CODEC;
    private static final MethodHandle CREATE_BLOCK_ENTITY_INFO;

    static {
        try {
            Field heightmaps = ClientboundLevelChunkPacketData.class.getDeclaredField("HEIGHTMAPS_STREAM_CODEC");
            heightmaps.setAccessible(true);
            @SuppressWarnings("unchecked")
            StreamCodec<RegistryFriendlyByteBuf, Map<Heightmap.Types, long[]>> heightmapsCodec =
                    (StreamCodec<RegistryFriendlyByteBuf, Map<Heightmap.Types, long[]>>) heightmaps.get(null);
            HEIGHTMAPS_CODEC = heightmapsCodec;

            Class<?> blockEntityInfo = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo"
            );
            Field listCodec = blockEntityInfo.getDeclaredField("LIST_STREAM_CODEC");
            listCodec.setAccessible(true);
            @SuppressWarnings("unchecked")
            StreamCodec<RegistryFriendlyByteBuf, List<Object>> blockEntityListCodec =
                    (StreamCodec<RegistryFriendlyByteBuf, List<Object>>) listCodec.get(null);
            BLOCK_ENTITY_LIST_CODEC = blockEntityListCodec;

            Method create = blockEntityInfo.getDeclaredMethod("create", BlockEntity.class);
            create.setAccessible(true);
            CREATE_BLOCK_ENTITY_INFO = MethodHandles.lookup().unreflect(create);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private NmsPacketAccess() {
    }

    static void encodeHeightmaps(RegistryFriendlyByteBuf buf, Map<Heightmap.Types, long[]> heightmaps) {
        HEIGHTMAPS_CODEC.encode(buf, heightmaps);
    }

    static void encodeBlockEntities(RegistryFriendlyByteBuf buf, List<Object> blockEntities) {
        BLOCK_ENTITY_LIST_CODEC.encode(buf, blockEntities);
    }

    static Object createBlockEntityInfo(BlockEntity blockEntity) {
        try {
            return CREATE_BLOCK_ENTITY_INFO.invoke(blockEntity);
        } catch (Throwable exception) {
            throw new IllegalStateException("Failed to encode block entity", exception);
        }
    }
}

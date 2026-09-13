package com.boggy.petalantifreecam.v26_2;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class NmsPacketAccess {

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<Heightmap.Types, long[]>> HEIGHTMAPS_CODEC;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Object>> BLOCK_ENTITY_LIST_CODEC;
    private static final MethodHandle GET_BLOCK_ENTITIES;
    private static final MethodHandle GET_BLOCK_ENTITY_Y;
    private static final MethodHandle CONFIGURATION_FOR_BITS;

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

            Field blockEntities = ClientboundLevelChunkPacketData.class.getDeclaredField("blockEntitiesData");
            blockEntities.setAccessible(true);
            GET_BLOCK_ENTITIES = MethodHandles.lookup().unreflectGetter(blockEntities);

            Field blockEntityY = blockEntityInfo.getDeclaredField("y");
            blockEntityY.setAccessible(true);
            GET_BLOCK_ENTITY_Y = MethodHandles.lookup().unreflectGetter(blockEntityY);

            Method configurationForBits = Strategy.class.getDeclaredMethod("getConfigurationForBitCount", int.class);
            configurationForBits.setAccessible(true);
            CONFIGURATION_FOR_BITS = MethodHandles.lookup().unreflect(configurationForBits);
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

    static List<Object> blockEntitiesAbove(ClientboundLevelChunkPacketData data, int hideBlocksBelowY) {
        try {
            @SuppressWarnings("unchecked")
            List<Object> all = (List<Object>) GET_BLOCK_ENTITIES.invoke(data);
            List<Object> kept = null;
            int size = all.size();
            for (int index = 0; index < size; index++) {
                Object info = all.get(index);
                int y = (int) GET_BLOCK_ENTITY_Y.invoke(info);
                if (y < hideBlocksBelowY) {
                    if (kept == null) {
                        kept = new ArrayList<>(size);
                        kept.addAll(all.subList(0, index));
                    }
                } else if (kept != null) {
                    kept.add(info);
                }
            }
            return kept == null ? all : kept;
        } catch (Throwable exception) {
            throw new IllegalStateException("Failed to read chunk block entities", exception);
        }
    }

    static void skipSection(FriendlyByteBuf buf, PalettedContainerFactory factory) {
        buf.readShort();
        buf.readShort();
        skipPalettedContainer(buf, factory.blockStatesStrategy());
        skipPalettedContainer(buf, factory.biomeStrategy());
    }

    private static void skipPalettedContainer(FriendlyByteBuf buf, Strategy<?> strategy) {
        Configuration configuration = configurationForBits(strategy, buf.readByte());
        if (!(configuration instanceof Configuration.Global)) {
            if (configuration.bitsInMemory() == 0) {
                buf.readVarInt();
            } else {
                int paletteSize = buf.readVarInt();
                for (int index = 0; index < paletteSize; index++) {
                    buf.readVarInt();
                }
            }
        }

        int memoryBits = configuration.bitsInMemory();
        if (memoryBits == 0) {
            return;
        }
        int valuesPerLong = Long.SIZE / memoryBits;
        int longCount = (strategy.entryCount() + valuesPerLong - 1) / valuesPerLong;
        buf.skipBytes(longCount * Long.BYTES);
    }

    private static Configuration configurationForBits(Strategy<?> strategy, int bits) {
        try {
            return (Configuration) CONFIGURATION_FOR_BITS.invoke(strategy, bits);
        } catch (Throwable exception) {
            throw new IllegalStateException("Failed to resolve palette configuration", exception);
        }
    }
}

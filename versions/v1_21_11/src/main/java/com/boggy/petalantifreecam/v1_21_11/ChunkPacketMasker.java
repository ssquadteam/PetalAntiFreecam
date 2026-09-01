package com.boggy.petalantifreecam.v1_21_11;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;

final class ChunkPacketMasker {

    private static final int SECTION_HEIGHT = 16;

    private final ThreadLocal<LevelChunkSection> partialScratch = new ThreadLocal<>();
    private volatile PalettedContainerFactory emptyFactory;
    private volatile byte[] emptySectionBytes;

    ClientboundLevelChunkWithLightPacket mask(
            ClientboundLevelChunkWithLightPacket original,
            ServerPlayer player,
            int hideBlocksBelowY
    ) {
        ServerLevel level = player.level();
        if (hideBlocksBelowY <= level.getMinY()) {
            return original;
        }

        ClientboundLevelChunkPacketData chunkData = original.getChunkData();
        FriendlyByteBuf in = chunkData.getReadBuffer();
        ByteBuf sectionBytes = Unpooled.buffer(Math.max(32, in.readableBytes()));
        ByteBuf packetBytes = Unpooled.buffer();
        try {
            FriendlyByteBuf sectionBuf = new FriendlyByteBuf(sectionBytes);
            writeMaskedSections(sectionBuf, in, level, hideBlocksBelowY);

            RegistryFriendlyByteBuf dataBuf = new RegistryFriendlyByteBuf(packetBytes, level.registryAccess());
            dataBuf.writeInt(original.getX());
            dataBuf.writeInt(original.getZ());
            NmsPacketAccess.encodeHeightmaps(dataBuf, chunkData.getHeightmaps());
            dataBuf.writeVarInt(sectionBytes.readableBytes());
            dataBuf.writeBytes(sectionBytes);
            NmsPacketAccess.encodeBlockEntities(dataBuf, NmsPacketAccess.blockEntitiesAbove(chunkData, hideBlocksBelowY));
            original.getLightData().write(dataBuf);

            ClientboundLevelChunkWithLightPacket masked = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(dataBuf);
            masked.setReady(true);
            return masked;
        } finally {
            in.release();
            sectionBytes.release();
            packetBytes.release();
        }
    }

    private void writeMaskedSections(
            FriendlyByteBuf out,
            FriendlyByteBuf in,
            ServerLevel level,
            int hideBlocksBelowY
    ) {
        PalettedContainerFactory factory = level.palettedContainerFactory();
        byte[] empty = emptySectionBytes(factory);
        int sectionCount = level.getSectionsCount();
        for (int sectionIndex = 0; sectionIndex < sectionCount; sectionIndex++) {
            int sectionMinY = level.getSectionYFromSectionIndex(sectionIndex) * SECTION_HEIGHT;
            if (sectionMinY >= hideBlocksBelowY) {
                out.writeBytes(in);
                return;
            }
            if (sectionMinY + (SECTION_HEIGHT - 1) < hideBlocksBelowY) {
                NmsPacketAccess.skipSection(in, factory);
                out.writeBytes(empty);
                continue;
            }
            writePartialSection(out, in, level, hideBlocksBelowY - sectionMinY, sectionIndex);
        }
    }

    private void writePartialSection(
            FriendlyByteBuf out,
            FriendlyByteBuf in,
            ServerLevel level,
            int hiddenLayers,
            int sectionIndex
    ) {
        LevelChunkSection scratch = partialScratch(level);
        scratch.read(in);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = 0; y < hiddenLayers; y++) {
            for (int z = 0; z < SECTION_HEIGHT; z++) {
                for (int x = 0; x < SECTION_HEIGHT; x++) {
                    scratch.setBlockState(x, y, z, air, false);
                }
            }
        }
        scratch.write(out, null, sectionIndex);
    }

    private LevelChunkSection partialScratch(ServerLevel level) {
        LevelChunkSection scratch = partialScratch.get();
        if (scratch == null) {
            scratch = new LevelChunkSection(level.palettedContainerFactory());
            partialScratch.set(scratch);
        }
        return scratch;
    }

    private byte[] emptySectionBytes(PalettedContainerFactory factory) {
        byte[] cached = emptySectionBytes;
        if (cached != null && emptyFactory == factory) {
            return cached;
        }
        synchronized (this) {
            cached = emptySectionBytes;
            if (cached != null && emptyFactory == factory) {
                return cached;
            }
            LevelChunkSection empty = new LevelChunkSection(factory);
            ByteBuf bytes = Unpooled.buffer(32);
            try {
                empty.write(new FriendlyByteBuf(bytes), null, 0);
                cached = new byte[bytes.readableBytes()];
                bytes.readBytes(cached);
            } finally {
                bytes.release();
            }
            emptyFactory = factory;
            emptySectionBytes = cached;
            return cached;
        }
    }
}

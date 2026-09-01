package com.boggy.petalantifreecam.v1_21_11;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.papermc.paper.FeatureHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayList;
import java.util.List;

final class ChunkPacketMasker {

    private static final int SECTION_HEIGHT = 16;

    private volatile LevelChunkSection emptyAirSection;

    ClientboundLevelChunkWithLightPacket mask(
            ClientboundLevelChunkWithLightPacket original,
            ServerPlayer player,
            int hideBlocksBelowY
    ) {
        ServerLevel level = player.level();
        if (hideBlocksBelowY <= level.getMinY()) {
            return original;
        }

        LevelChunk chunk = level.getChunkIfLoaded(original.getX(), original.getZ());
        if (chunk == null) {
            return original;
        }

        ByteBuf sectionBytes = Unpooled.buffer();
        ByteBuf packetBytes = Unpooled.buffer();
        try {
            FriendlyByteBuf sectionBuf = new FriendlyByteBuf(sectionBytes);
            writeMaskedSections(sectionBuf, chunk, hideBlocksBelowY);

            RegistryFriendlyByteBuf dataBuf = new RegistryFriendlyByteBuf(packetBytes, level.registryAccess());
            NmsPacketAccess.encodeHeightmaps(dataBuf, original.getChunkData().getHeightmaps());
            dataBuf.writeVarInt(sectionBytes.readableBytes());
            dataBuf.writeBytes(sectionBytes);
            NmsPacketAccess.encodeBlockEntities(dataBuf, blockEntitiesAbove(chunk, hideBlocksBelowY));
            packetBytes.readerIndex(0);

            ClientboundLevelChunkPacketData maskedData = new ClientboundLevelChunkPacketData(
                    dataBuf,
                    original.getX(),
                    original.getZ()
            );

            packetBytes.clear();
            dataBuf.writeInt(original.getX());
            dataBuf.writeInt(original.getZ());
            maskedData.write(dataBuf);
            original.getLightData().write(dataBuf);

            ClientboundLevelChunkWithLightPacket masked = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(dataBuf);
            masked.setReady(true);
            return masked;
        } finally {
            sectionBytes.release();
            packetBytes.release();
        }
    }

    private void writeMaskedSections(FriendlyByteBuf buf, LevelChunk chunk, int hideBlocksBelowY) {
        LevelChunkSection[] sections = chunk.getSections();
        ServerLevel level = (ServerLevel) chunk.getLevel();
        LevelChunkSection empty = emptyAir(chunk);

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            int sectionMinY = level.getSectionYFromSectionIndex(sectionIndex) * SECTION_HEIGHT;
            LevelChunkSection section = sections[sectionIndex];
            if (sectionMinY + (SECTION_HEIGHT - 1) < hideBlocksBelowY) {
                writeSection(buf, empty, sectionIndex);
                continue;
            }
            if (sectionMinY >= hideBlocksBelowY) {
                writeSection(buf, section, sectionIndex);
                continue;
            }
            writePartialSection(buf, section, sectionIndex, hideBlocksBelowY - sectionMinY);
        }
    }

    private static void writePartialSection(FriendlyByteBuf buf, LevelChunkSection section, int sectionIndex, int hiddenLayers) {
        LevelChunkSection copy = section.copy();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = 0; y < hiddenLayers; y++) {
            for (int z = 0; z < SECTION_HEIGHT; z++) {
                for (int x = 0; x < SECTION_HEIGHT; x++) {
                    copy.setBlockState(x, y, z, air, false);
                }
            }
        }
        writeSection(buf, copy, sectionIndex);
    }

    private static void writeSection(FriendlyByteBuf buf, LevelChunkSection section, int sectionIndex) {
        section.write(buf, null, sectionIndex);
    }

    private static List<Object> blockEntitiesAbove(LevelChunk chunk, int hideBlocksBelowY) {
        List<Object> infos = new ArrayList<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity.getBlockPos().getY() >= hideBlocksBelowY) {
                infos.add(NmsPacketAccess.createBlockEntityInfo(blockEntity));
            }
        }
        return infos;
    }

    private LevelChunkSection emptyAir(LevelChunk chunk) {
        LevelChunkSection cached = emptyAirSection;
        if (cached == null) {
            ServerLevel level = (ServerLevel) chunk.getLevel();
            cached = FeatureHooks.createSection(level.palettedContainerFactory(), level, chunk.getPos(), 0);
            emptyAirSection = cached;
        }
        return cached;
    }
}

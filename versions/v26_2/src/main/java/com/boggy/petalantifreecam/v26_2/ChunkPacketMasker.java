package com.boggy.petalantifreecam.v26_2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.biome.Biome;

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
        LevelChunkSection empty = emptyAir(level);

        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            int sectionMinY = level.getSectionYFromSectionIndex(sectionIndex) * SECTION_HEIGHT;
            LevelChunkSection section = sections[sectionIndex];
            if (sectionMinY + (SECTION_HEIGHT - 1) < hideBlocksBelowY) {
                empty.write(buf, null, sectionIndex);
                continue;
            }
            if (sectionMinY >= hideBlocksBelowY) {
                section.write(buf, null, sectionIndex);
                continue;
            }
            writePartialSection(buf, section, sectionIndex, hideBlocksBelowY - sectionMinY);
        }
    }

    private static void writePartialSection(FriendlyByteBuf buf, LevelChunkSection section, int sectionIndex, int hiddenLayers) {
        PalettedContainer<BlockState> states = section.getStates().copy();
        @SuppressWarnings("unchecked")
        PalettedContainer<Holder<Biome>> biomes = (PalettedContainer<Holder<Biome>>) section.getBiomes();
        LevelChunkSection copy = new LevelChunkSection(states, biomes);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = 0; y < hiddenLayers; y++) {
            for (int z = 0; z < SECTION_HEIGHT; z++) {
                for (int x = 0; x < SECTION_HEIGHT; x++) {
                    copy.setBlockState(x, y, z, air, false);
                }
            }
        }
        copy.write(buf, null, sectionIndex);
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

    private LevelChunkSection emptyAir(ServerLevel level) {
        LevelChunkSection cached = emptyAirSection;
        if (cached == null) {
            cached = new LevelChunkSection(level.palettedContainerFactory());
            emptyAirSection = cached;
        }
        return cached;
    }
}

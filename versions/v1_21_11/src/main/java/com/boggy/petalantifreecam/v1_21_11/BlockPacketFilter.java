package com.boggy.petalantifreecam.v1_21_11;

import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;

final class BlockPacketFilter {

    private static final int SECTION_HEIGHT = 16;

    private BlockPacketFilter() {
    }

    static Object filter(Object packet, int hideBlocksBelowY) {
        if (packet instanceof ClientboundBlockUpdatePacket blockUpdate) {
            return blockUpdate.getPos().getY() < hideBlocksBelowY ? null : blockUpdate;
        }
        if (packet instanceof ClientboundBlockEventPacket blockEvent) {
            return blockEvent.getPos().getY() < hideBlocksBelowY ? null : blockEvent;
        }
        if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            return filterSection(sectionUpdate, hideBlocksBelowY);
        }
        return packet;
    }

    private static Object filterSection(ClientboundSectionBlocksUpdatePacket packet, int hideBlocksBelowY) {
        Short2ObjectMap<BlockState> kept = new Short2ObjectOpenHashMap<>();
        SectionPos[] sectionPos = new SectionPos[1];

        packet.runUpdates((BlockPos pos, BlockState state) -> {
            if (sectionPos[0] == null) {
                sectionPos[0] = SectionPos.of(pos);
            }
            if (pos.getY() >= hideBlocksBelowY) {
                kept.put(SectionPos.sectionRelativePos(pos), state);
            }
        });

        if (sectionPos[0] == null) {
            return packet;
        }

        int sectionMinY = sectionPos[0].minBlockY();
        if (sectionMinY >= hideBlocksBelowY) {
            return packet;
        }
        if (sectionMinY + (SECTION_HEIGHT - 1) < hideBlocksBelowY) {
            return null;
        }
        if (kept.isEmpty()) {
            return null;
        }
        return new ClientboundSectionBlocksUpdatePacket(sectionPos[0], kept);
    }
}

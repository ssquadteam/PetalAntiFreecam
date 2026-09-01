package com.boggy.petalantifreecam.v1_21_11;

import io.papermc.paper.FeatureHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;

final class PlayerChunkRefresher {

    private static final int SECTION_HEIGHT = 16;

    private PlayerChunkRefresher() {
    }

    static void refresh(Player player, World world, int chunkX, int chunkZ, int hideBlocksBelowY) {
        if (!(player instanceof CraftPlayer craftPlayer) || !(world instanceof CraftWorld craftWorld)) {
            return;
        }

        ServerLevel level = craftWorld.getHandle();
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null || !hasHiddenBlocks(chunk, hideBlocksBelowY)) {
            return;
        }

        ServerPlayer serverPlayer = craftPlayer.getHandle();
        if (serverPlayer.connection == null) {
            return;
        }

        FeatureHooks.sendChunkRefreshPackets(List.of(serverPlayer), chunk);
    }

    private static boolean hasHiddenBlocks(LevelChunk chunk, int hideBlocksBelowY) {
        ServerLevel level = (ServerLevel) chunk.getLevel();
        if (hideBlocksBelowY <= level.getMinY()) {
            return false;
        }

        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            int sectionMinY = level.getSectionYFromSectionIndex(sectionIndex) * SECTION_HEIGHT;
            if (sectionMinY >= hideBlocksBelowY) {
                return false;
            }
            LevelChunkSection section = sections[sectionIndex];
            if (section != null && !section.hasOnlyAir()) {
                return true;
            }
        }
        return false;
    }
}

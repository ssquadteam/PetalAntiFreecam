package com.boggy.petalantifreecam.v1_21_11;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.nms.NmsAccess;
import com.boggy.petalantifreecam.nms.PacketInterceptor;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class V1_21_11NmsAccess implements NmsAccess {

    @Override
    public boolean supports(String minecraftVersion) {
        return minecraftVersion.startsWith("1.21.11");
    }

    @Override
    public PacketInterceptor createInterceptor(
            Plugin plugin,
            PlayerVisibilityManager visibility,
            ConfigurationManager configuration
    ) {
        return new V1_21_11PacketInterceptor(plugin, visibility, configuration);
    }

    @Override
    public void refreshChunkForPlayer(Player player, World world, int chunkX, int chunkZ, int hideBlocksBelowY) {
        PlayerChunkRefresher.refresh(player, world, chunkX, chunkZ, hideBlocksBelowY);
    }
}

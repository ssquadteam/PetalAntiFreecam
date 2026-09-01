package com.boggy.petalantifreecam.nms;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface NmsAccess {

    boolean supports(String minecraftVersion);

    PacketInterceptor createInterceptor(
            Plugin plugin,
            PlayerVisibilityManager visibility,
            ConfigurationManager configuration
    );

    void refreshChunkForPlayer(Player player, World world, int chunkX, int chunkZ, int hideBlocksBelowY);
}

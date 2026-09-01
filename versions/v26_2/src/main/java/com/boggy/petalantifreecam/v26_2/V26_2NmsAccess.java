package com.boggy.petalantifreecam.v26_2;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.nms.NmsAccess;
import com.boggy.petalantifreecam.nms.PacketInterceptor;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import org.bukkit.plugin.Plugin;

public final class V26_2NmsAccess implements NmsAccess {

    @Override
    public boolean supports(String minecraftVersion) {
        return minecraftVersion.startsWith("26.2") || minecraftVersion.startsWith("1.21.11");
    }

    @Override
    public PacketInterceptor createInterceptor(
            Plugin plugin,
            PlayerVisibilityManager visibility,
            ConfigurationManager configuration
    ) {
        return new V26_2PacketInterceptor(plugin, visibility, configuration);
    }
}

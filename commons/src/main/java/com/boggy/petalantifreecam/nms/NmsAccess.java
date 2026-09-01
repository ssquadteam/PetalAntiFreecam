package com.boggy.petalantifreecam.nms;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import org.bukkit.plugin.Plugin;

public interface NmsAccess {

    boolean supports(String minecraftVersion);

    PacketInterceptor createInterceptor(
            Plugin plugin,
            PlayerVisibilityManager visibility,
            ConfigurationManager configuration
    );
}

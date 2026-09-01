package com.boggy.petalantifreecam.nms;

import org.bukkit.entity.Player;

public interface PacketInterceptor {

    void inject(Player player);

    void uninject(Player player);

    void uninjectAll();
}

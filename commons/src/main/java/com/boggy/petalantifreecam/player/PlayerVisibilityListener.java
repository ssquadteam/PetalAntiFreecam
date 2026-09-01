package com.boggy.petalantifreecam.player;

import com.boggy.petalantifreecam.nms.PacketInterceptor;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PlayerVisibilityListener implements Listener {

    private final PlayerVisibilityManager visibilityService;
    private final PacketInterceptor packetInterceptor;

    public PlayerVisibilityListener(PlayerVisibilityManager visibilityService, PacketInterceptor packetInterceptor) {
        this.visibilityService = visibilityService;
        this.packetInterceptor = packetInterceptor;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        visibilityService.track(event.getPlayer());
        packetInterceptor.inject(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        packetInterceptor.uninject(event.getPlayer());
        visibilityService.untrack(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent) {
            return;
        }
        updateWhenHeightChanges(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        updateWhenHeightChanges(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        visibilityService.update(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        visibilityService.update(event.getPlayer(), event.getPlayer().getLocation());
    }

    private void updateWhenHeightChanges(PlayerMoveEvent event) {
        Location destination = event.getTo();
        if (destination == null) {
            return;
        }
        if (event.getFrom().getWorld().equals(destination.getWorld()) &&
                event.getFrom().getBlockY() == destination.getBlockY()) {
            return;
        }

        visibilityService.update(event.getPlayer(), destination);
    }
}

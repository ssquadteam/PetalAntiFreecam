package com.boggy.petalantifreecam.player;

import io.canvasmc.canvas.event.EntityPostTeleportAsyncEvent;
import io.canvasmc.canvas.event.EntityTeleportAsyncEvent;
import io.canvasmc.canvas.event.PlayerPostRespawnAsyncEvent;
import io.canvasmc.canvas.event.PlayerRespawnAsyncEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class CanvasVisibilityListener implements Listener {

    private final PlayerVisibilityManager visibilityService;

    public CanvasVisibilityListener(PlayerVisibilityManager visibilityService) {
        this.visibilityService = visibilityService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTeleportAsync(final EntityTeleportAsyncEvent event) {
        if (event.getEntity() instanceof Player player)
            visibilityService.handleTeleport(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPostTeleportAsync(final EntityPostTeleportAsyncEvent event) {
        if (event.getEntity() instanceof Player player)
            visibilityService.handleTeleport(player, event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerRespawnAsync(final PlayerRespawnAsyncEvent event) {
        visibilityService.handleTeleport(event.getPlayer(), event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPostRespawnAsync(final PlayerPostRespawnAsyncEvent event) {
        visibilityService.handleTeleport(event.getPlayer(), event.getRespawnLocation());
    }
}

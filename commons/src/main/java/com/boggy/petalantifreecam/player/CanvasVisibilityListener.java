package com.boggy.petalantifreecam.player;

import io.canvasmc.canvas.event.EntityPostTeleportAsyncEvent;
import io.canvasmc.canvas.event.PlayerPostRespawnAsyncEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class CanvasVisibilityListener implements Listener {

    private final PlayerVisibilityManager visibilityService;

    public CanvasVisibilityListener(PlayerVisibilityManager visibilityService) {
        this.visibilityService = visibilityService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPostTeleportAsync(EntityPostTeleportAsyncEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Location destination = event.getTo();
        if (destination == null) {
            return;
        }
        visibilityService.update(player, destination);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPostRespawnAsync(PlayerPostRespawnAsyncEvent event) {
        visibilityService.update(event.getPlayer(), event.getRespawnLocation());
    }
}

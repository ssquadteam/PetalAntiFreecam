package com.boggy.petalantifreecam.player;

import com.boggy.petalantifreecam.config.AntiFreecamSettings;
import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.refresh.ChunkRefreshScheduler;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerVisibilityManager {

    private final ConfigurationManager configurationManager;
    private final ChunkRefreshScheduler refreshScheduler;
    private final Set<UUID> maskingPlayers = ConcurrentHashMap.newKeySet();

    public PlayerVisibilityManager(ConfigurationManager configurationManager, ChunkRefreshScheduler refreshScheduler) {
        this.configurationManager = configurationManager;
        this.refreshScheduler = refreshScheduler;
    }

    public boolean isMasking(UUID playerId) {
        return maskingPlayers.contains(playerId);
    }

    public void track(Player player) {
        AntiFreecamSettings settings = configurationManager.current();

        if (shouldMask(player.getLocation().getY(), settings)) {
            maskingPlayers.add(player.getUniqueId());
            return;
        }

        maskingPlayers.remove(player.getUniqueId());
    }

    public void update(Player player, Location location) {
        UUID playerId = player.getUniqueId();
        AntiFreecamSettings settings = configurationManager.current();

        boolean wasMasking = maskingPlayers.contains(playerId);
        boolean shouldMask = shouldMask(location.getY(), settings);

        if (wasMasking == shouldMask) {
            return;
        }

        setMasking(playerId, shouldMask);
        if (shouldMask) {
            return;
        }
        refreshScheduler.executeForPlayer(player, () -> refreshScheduler.enqueue(player), 2L);
    }

    public void untrack(Player player) {
        maskingPlayers.remove(player.getUniqueId());
        refreshScheduler.cancel(player);
    }

    public void applyReload(Collection<? extends Player> players) {
        AntiFreecamSettings currentSettings = configurationManager.current();

        for (Player player : players) {
            refreshScheduler.executeForPlayer(player, () -> applyReload(player, currentSettings));
        }
    }

    public void clear() {
        maskingPlayers.clear();
    }

    private boolean shouldMask(double playerY, AntiFreecamSettings settings) {
        return playerY >= settings.restoreBelowY();
    }

    private void applyReload(Player player, AntiFreecamSettings settings) {
        UUID playerId = player.getUniqueId();
        boolean wasMasking = maskingPlayers.contains(playerId);
        boolean shouldMask = shouldMask(player.getLocation().getY(), settings);
        setMasking(playerId, shouldMask);
        if (wasMasking && !shouldMask) {
            refreshScheduler.enqueue(player);
        }
    }

    private void setMasking(UUID playerId, boolean masking) {
        if (masking) {
            maskingPlayers.add(playerId);
            return;
        }

        maskingPlayers.remove(playerId);
    }
}

package com.boggy.petalantifreecam.refresh;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ChunkRefreshScheduler {

    private final Plugin plugin;
    private final ConfigurationManager configurationManager;
    private final Queue<PlayerChunkRefreshQueue> playerRefreshQueues = new ConcurrentLinkedQueue<>();
    private final Set<ChunkRefreshKey> pendingRefreshes = ConcurrentHashMap.newKeySet();
    private ScheduledTask task;

    public ChunkRefreshScheduler(Plugin plugin, ConfigurationManager configurationManager) {
        this.plugin = plugin;
        this.configurationManager = configurationManager;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = plugin.getServer().getGlobalRegionScheduler().
                runAtFixedRate(plugin, ignored -> processBudget(), 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        playerRefreshQueues.clear();
        pendingRefreshes.clear();
    }

    public void enqueue(Player player) {
        World world = player.getWorld();
        List<ChunkRefreshKey> refreshKeys = new ArrayList<>();

        for (long chunkKey : player.getSentChunkKeys()) {
            ChunkRefreshKey refreshKey = new ChunkRefreshKey(world.getUID(), chunkKey);
            if (pendingRefreshes.add(refreshKey)) {
                refreshKeys.add(refreshKey);
            }
        }

        if (refreshKeys.isEmpty()) {
            return;
        }

        Location playerLocation = player.getLocation();
        int playerChunkX = playerLocation.getBlockX() >> 4;
        int playerChunkZ = playerLocation.getBlockZ() >> 4;
        refreshKeys.sort(Comparator.comparingLong(
                refreshKey -> distanceSquared(refreshKey, playerChunkX, playerChunkZ)
        ));
        playerRefreshQueues.add(new PlayerChunkRefreshQueue(refreshKeys));
    }

    public void executeForPlayer(Player player, Runnable action) {
        executeForPlayer(player, action, 1L);
    }

    public void executeForPlayer(Player player, Runnable action, long delayTicks) {
        player.getScheduler().execute(plugin, action, null, delayTicks);
    }

    private void processBudget() {
        int budget = configurationManager.current().chunkRefreshBudgetPerTick();
        for (int processed = 0; processed < budget; processed++) {
            PlayerChunkRefreshQueue playerQueue = playerRefreshQueues.poll();
            if (playerQueue == null) {
                return;
            }

            ChunkRefreshKey refreshKey = playerQueue.poll();
            scheduleRefresh(refreshKey);

            if (playerQueue.hasRemaining()) {
                playerRefreshQueues.add(playerQueue);
            }
        }
    }

    private static long distanceSquared(ChunkRefreshKey refreshKey, int playerChunkX, int playerChunkZ) {
        long chunkXDistance = (int) refreshKey.chunkKey() - playerChunkX;
        long chunkZDistance = (int) (refreshKey.chunkKey() >>> 32) - playerChunkZ;
        return chunkXDistance * chunkXDistance + chunkZDistance * chunkZDistance;
    }

    private void scheduleRefresh(ChunkRefreshKey refreshKey) {
        World world = plugin.getServer().getWorld(refreshKey.worldId());
        if (world == null) {
            pendingRefreshes.remove(refreshKey);
            return;
        }

        int chunkX = (int) refreshKey.chunkKey();
        int chunkZ = (int) (refreshKey.chunkKey() >>> 32);

        if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            pendingRefreshes.remove(refreshKey);
            refresh(world, chunkX, chunkZ);
            return;
        }

        plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () -> {
            pendingRefreshes.remove(refreshKey);
            refresh(world, chunkX, chunkZ);
        });
    }

    private void refresh(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        if (world.getPlayersSeeingChunk(chunkX, chunkZ).isEmpty()) {
            return;
        }

        world.refreshChunk(chunkX, chunkZ);
    }
}

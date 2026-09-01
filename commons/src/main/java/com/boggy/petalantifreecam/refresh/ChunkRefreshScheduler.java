package com.boggy.petalantifreecam.refresh;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.nms.NmsAccess;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ChunkRefreshScheduler {

    private final Plugin plugin;
    private final ConfigurationManager configurationManager;
    private final NmsAccess nmsAccess;
    private final Queue<PlayerChunkRefreshQueue> playerRefreshQueues = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<UUID, Integer> enqueueGenerations = new ConcurrentHashMap<>();
    private ScheduledTask task;

    public ChunkRefreshScheduler(Plugin plugin, ConfigurationManager configurationManager, NmsAccess nmsAccess) {
        this.plugin = plugin;
        this.configurationManager = configurationManager;
        this.nmsAccess = nmsAccess;
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
        enqueueGenerations.clear();
    }

    public void enqueue(Player player) {
        World world = player.getWorld();
        UUID playerId = player.getUniqueId();
        int generation = enqueueGenerations.merge(playerId, 1, Integer::sum);
        List<ChunkRefreshKey> refreshKeys = new ArrayList<>();

        UUID worldId = world.getUID();
        for (long chunkKey : player.getSentChunkKeys()) {
            refreshKeys.add(new ChunkRefreshKey(playerId, worldId, chunkKey));
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
        playerRefreshQueues.add(new PlayerChunkRefreshQueue(generation, refreshKeys));
    }

    public void cancel(Player player) {
        enqueueGenerations.remove(player.getUniqueId());
    }

    public void executeForPlayer(Player player, Runnable action) {
        executeForPlayer(player, action, 1L);
    }

    public void executeForPlayer(Player player, Runnable action, long delayTicks) {
        player.getScheduler().execute(plugin, action, null, delayTicks);
    }

    private void processBudget() {
        int budget = configurationManager.current().chunkRefreshBudgetPerTick();
        for (int processed = 0; processed < budget; ) {
            PlayerChunkRefreshQueue playerQueue = playerRefreshQueues.poll();
            if (playerQueue == null) {
                return;
            }

            ChunkRefreshKey refreshKey = playerQueue.poll();
            Integer currentGeneration = enqueueGenerations.get(refreshKey.playerId());
            if (currentGeneration == null || currentGeneration != playerQueue.generation()) {
                continue;
            }

            scheduleRefresh(refreshKey);
            processed++;

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
            return;
        }

        int chunkX = (int) refreshKey.chunkKey();
        int chunkZ = (int) (refreshKey.chunkKey() >>> 32);
        int hideBlocksBelowY = configurationManager.current().hideBlocksBelowY();

        if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
            refresh(refreshKey, world, chunkX, chunkZ, hideBlocksBelowY);
            return;
        }

        plugin.getServer().getRegionScheduler().execute(plugin, world, chunkX, chunkZ, () ->
                refresh(refreshKey, world, chunkX, chunkZ, hideBlocksBelowY)
        );
    }

    private void refresh(ChunkRefreshKey refreshKey, World world, int chunkX, int chunkZ, int hideBlocksBelowY) {
        Player player = plugin.getServer().getPlayer(refreshKey.playerId());
        if (player == null || !player.isOnline() || !player.getWorld().equals(world)) {
            return;
        }
        if (!player.isChunkSent(refreshKey.chunkKey())) {
            return;
        }

        nmsAccess.refreshChunkForPlayer(player, world, chunkX, chunkZ, hideBlocksBelowY);
    }
}

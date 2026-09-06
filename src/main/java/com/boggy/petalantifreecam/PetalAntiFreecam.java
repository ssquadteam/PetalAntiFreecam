package com.boggy.petalantifreecam;

import com.boggy.petalantifreecam.command.AntiFreecamCommand;
import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.packet.AirBlockStateIds;
import com.boggy.petalantifreecam.packet.ChunkMasker;
import com.boggy.petalantifreecam.packet.ChunkPacketListener;
import com.boggy.petalantifreecam.player.CanvasVisibilityListener;
import com.boggy.petalantifreecam.player.PlayerVisibilityListener;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import com.boggy.petalantifreecam.refresh.ChunkRefreshScheduler;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PetalAntiFreecam extends JavaPlugin {

    private ChunkRefreshScheduler refreshScheduler;
    private PlayerVisibilityManager visibilityService;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PacketEvents.getAPI().init();

        ConfigurationManager configurationManager;
        try {
            configurationManager = new ConfigurationManager(this);
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        refreshScheduler = new ChunkRefreshScheduler(this, configurationManager);
        visibilityService = new PlayerVisibilityManager(configurationManager, refreshScheduler);

        getServer().getPluginManager().registerEvents(new PlayerVisibilityListener(visibilityService), this);

        try {
            Class.forName("io.canvasmc.canvas.event.EntityTeleportAsyncEvent");
            getServer().getPluginManager().registerEvents(new CanvasVisibilityListener(visibilityService), this);
            getLogger().info("Detected CanvasMC - registered Canvas visibility listeners.");
        } catch (final ClassNotFoundException ignored) {}

        ChunkPacketListener chunkPacketListener = new ChunkPacketListener(
                visibilityService,
                configurationManager,
                new ChunkMasker(AirBlockStateIds.forVersion(
                        PacketEvents.getAPI().getServerManager().getVersion().toClientVersion()
                ))
        );

        PacketEvents.getAPI().getEventManager().registerListener(chunkPacketListener, PacketListenerPriority.HIGHEST);

        AntiFreecamCommand antiFreecamCommand = new AntiFreecamCommand(configurationManager, visibilityService, getServer());

        PluginCommand command = Objects.requireNonNull(getCommand("antifreecam"));
        command.setExecutor(antiFreecamCommand);
        command.setTabCompleter(antiFreecamCommand);

        refreshScheduler.start();
        getServer().getOnlinePlayers().forEach(player ->
                refreshScheduler.executeForPlayer(player, () -> visibilityService.track(player))
        );
    }

    @Override
    public void onDisable() {
        if (refreshScheduler != null) {
            refreshScheduler.stop();
        }

        if (visibilityService != null) {
            visibilityService.clear();
        }

        PacketEvents.getAPI().terminate();
    }
}

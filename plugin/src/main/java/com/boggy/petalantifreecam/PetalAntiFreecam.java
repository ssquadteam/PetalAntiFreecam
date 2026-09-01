package com.boggy.petalantifreecam;

import com.boggy.petalantifreecam.command.AntiFreecamCommand;
import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.nms.NmsAccess;
import com.boggy.petalantifreecam.nms.PacketInterceptor;
import com.boggy.petalantifreecam.player.PlayerVisibilityListener;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import com.boggy.petalantifreecam.refresh.ChunkRefreshScheduler;
import com.boggy.petalantifreecam.v26_2.V26_2NmsAccess;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class PetalAntiFreecam extends JavaPlugin {

    private static final List<NmsAccess> ACCESSES = List.of(new V26_2NmsAccess());

    private ChunkRefreshScheduler refreshScheduler;
    private PlayerVisibilityManager visibilityService;
    private PacketInterceptor packetInterceptor;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        NmsAccess nmsAccess = resolveNmsAccess();
        if (nmsAccess == null) {
            getLogger().severe("Unsupported Minecraft version: " + Bukkit.getMinecraftVersion());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ConfigurationManager configurationManager;
        try {
            configurationManager = new ConfigurationManager(this);
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        refreshScheduler = new ChunkRefreshScheduler(this, configurationManager, nmsAccess);
        visibilityService = new PlayerVisibilityManager(configurationManager, refreshScheduler);
        packetInterceptor = nmsAccess.createInterceptor(this, visibilityService, configurationManager);

        getServer().getPluginManager().registerEvents(
                new PlayerVisibilityListener(visibilityService, packetInterceptor),
                this
        );

        AntiFreecamCommand antiFreecamCommand = new AntiFreecamCommand(configurationManager, visibilityService, getServer());
        PluginCommand command = Objects.requireNonNull(getCommand("antifreecam"));
        command.setExecutor(antiFreecamCommand);
        command.setTabCompleter(antiFreecamCommand);

        refreshScheduler.start();
        getServer().getOnlinePlayers().forEach(player ->
                refreshScheduler.executeForPlayer(player, () -> {
                    visibilityService.track(player);
                    packetInterceptor.inject(player);
                })
        );
    }

    @Override
    public void onDisable() {
        if (packetInterceptor != null) {
            packetInterceptor.uninjectAll();
        }
        if (refreshScheduler != null) {
            refreshScheduler.stop();
        }
        if (visibilityService != null) {
            visibilityService.clear();
        }
    }

    private static NmsAccess resolveNmsAccess() {
        String minecraftVersion = Bukkit.getMinecraftVersion();
        for (NmsAccess access : ACCESSES) {
            if (access.supports(minecraftVersion)) {
                return access;
            }
        }
        return null;
    }
}

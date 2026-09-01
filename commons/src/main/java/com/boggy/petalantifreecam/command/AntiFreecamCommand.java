package com.boggy.petalantifreecam.command;

import com.boggy.petalantifreecam.config.ConfigurationManager;
import com.boggy.petalantifreecam.player.PlayerVisibilityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AntiFreecamCommand implements CommandExecutor, TabCompleter {

    private static final String RELOAD_PERMISSION = "petalantifreecam.reload";

    private final ConfigurationManager configurationManager;
    private final PlayerVisibilityManager visibilityService;
    private final Server server;

    public AntiFreecamCommand(ConfigurationManager configurationManager, PlayerVisibilityManager visibilityService, Server server) {
        this.configurationManager = Objects.requireNonNull(configurationManager);
        this.visibilityService = Objects.requireNonNull(visibilityService);
        this.server = Objects.requireNonNull(server);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text("Usage: /" + label + " reload", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        configurationManager.reload();
        visibilityService.applyReload(server.getOnlinePlayers());
        sender.sendMessage(Component.text("PetalAntiFreecam configuration reloaded.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1 || !sender.hasPermission(RELOAD_PERMISSION)) {
            return Collections.emptyList();
        }

        String input = args[0].toLowerCase(Locale.ROOT);
        return "reload".startsWith(input) ? List.of("reload") : Collections.emptyList();
    }
}

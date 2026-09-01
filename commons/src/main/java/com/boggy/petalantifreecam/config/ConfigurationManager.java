package com.boggy.petalantifreecam.config;

import org.bukkit.plugin.java.JavaPlugin;

public final class ConfigurationManager {

    private final JavaPlugin plugin;
    private volatile AntiFreecamSettings settings;

    public ConfigurationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.settings = AntiFreecamSettings.from(plugin.getConfig());
    }

    public AntiFreecamSettings current() {
        return settings;
    }

    public void reload() {
        plugin.reloadConfig();
        settings = AntiFreecamSettings.from(plugin.getConfig());
    }
}

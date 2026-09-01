package com.boggy.petalantifreecam.config;

import org.bukkit.configuration.Configuration;

public record AntiFreecamSettings(int restoreBelowY, int hideBlocksBelowY, int chunkRefreshBudgetPerTick) {

    public AntiFreecamSettings {
        if (chunkRefreshBudgetPerTick < 1) {
            throw new IllegalArgumentException("chunk-refresh-budget-per-tick must be at least 1");
        }
    }

    public static AntiFreecamSettings from(Configuration configuration) {
        return new AntiFreecamSettings(
                configuration.getInt("restore-below-y"),
                configuration.getInt("hide-blocks-below-y"),
                configuration.getInt("chunk-refresh-budget-per-tick")
        );
    }
}

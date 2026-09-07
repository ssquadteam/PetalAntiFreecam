dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.canvasmc.canvas:canvas-api:1.21.11-R0.1-SNAPSHOT")
}

configurations.configureEach {
    resolutionStrategy.capabilitiesResolution.withCapability("org.bukkit:bukkit") {
        select("io.canvasmc.canvas:canvas-api:0")
    }
}

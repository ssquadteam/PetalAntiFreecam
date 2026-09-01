pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.canvasmc.io/public")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "PetalAntiFreecam"

include("commons")
include("plugin")
include("versions:v26_2")
include("versions:v1_21_11")

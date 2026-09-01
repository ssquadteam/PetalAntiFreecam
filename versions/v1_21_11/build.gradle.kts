plugins {
    id("io.canvasmc.weaver.userdev") version "2.4.5"
}

dependencies {
    api(project(":commons"))
    paperweight.canvasDevBundle("1.21.11-R0.1-SNAPSHOT")
}

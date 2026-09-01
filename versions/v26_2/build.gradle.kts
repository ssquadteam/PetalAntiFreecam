plugins {
    id("io.canvasmc.weaver.userdev") version "2.4.5"
}

dependencies {
    api(project(":commons"))
    paperweight.paperDevBundle("26.2.build.+")
}

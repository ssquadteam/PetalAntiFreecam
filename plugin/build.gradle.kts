plugins {
    id("com.gradleup.shadow") version "9.6.1"
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation(project(":commons"))
    implementation(project(":versions:v26_2"))
    implementation(project(":versions:v1_21_11"))
}

tasks {
    processResources {
        val props = mapOf("version" to version.toString())
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("PetalAntiFreecam")
        mergeServiceFiles()
    }

    assemble {
        dependsOn(shadowJar)
    }

    build {
        dependsOn(shadowJar)
    }
}

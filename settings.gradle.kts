pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven {
            name = "Kikugie's Maven"
            url = uri("https://maven.kikugie.dev/snapshots")
        }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7-alpha.22"
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    shared {
        versions(
            "26.2",
        )
    }
    create(rootProject)
}

rootProject.name = "Reden"

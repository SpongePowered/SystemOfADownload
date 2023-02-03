

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.github.johnrengelman.shadow") version "7.1.2"
        id("io.micronaut.application") version "3.7.0"
        id("io.micronaut.test-resources") version "3.7.0"
    }
}
rootProject.name="systemofadownload"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT) // needed for forge-loom, unfortunately
    repositories {
        mavenCentral()
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            name = "sponge"
        }
    }
}

include(
    "artifacts",
    "artifacts:api",
    "artifacts:worker",
    "artifacts:server",
    "artifacts:events")
include("akka")

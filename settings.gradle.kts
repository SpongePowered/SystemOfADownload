

rootProject.name="SystemOfADownload"

dependencyResolutionManagement {
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
    "artifacts:events",
    "events",
    "events:outbox",
    "groups",
    "groups:api",
    "groups:events",
    "groups:worker",
    )

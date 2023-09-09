

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
    "akka",
    "akka:testkit",
    "artifacts",
    "artifacts:api",
    "artifacts:worker",
    "artifacts:server",
    "artifacts:events",
    )

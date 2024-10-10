

rootProject.name="SystemOfADownload"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.spongepowered.org/repository/maven-public/") {
            name = "sponge"
        }
        maven("https://repo.akka.io/maven") {
            content {
                includeGroup("com.typesafe.akka")
                includeGroup("com.lightbend.akka")
            }
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

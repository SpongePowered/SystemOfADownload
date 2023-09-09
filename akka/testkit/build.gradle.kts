


plugins {
    id("com.github.johnrengelman.shadow")
    id("io.micronaut.library")
}
dependencies {
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    api("io.micronaut:micronaut-inject")
    api(project(":akka"))
    api(libs.akka.testkit)
}

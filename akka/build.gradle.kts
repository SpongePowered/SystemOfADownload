
version = "0.1"
group = "org.spongepowered.downloads"

plugins {
    id("com.github.johnrengelman.shadow")
    id("io.micronaut.library")
}

dependencies {
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    api("io.micronaut:micronaut-inject")
    api(platform(libs.akkaBom))
    api(libs.bundles.actors)
    implementation(libs.bundles.akkaManagement)
}

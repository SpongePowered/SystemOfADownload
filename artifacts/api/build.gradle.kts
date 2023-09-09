
version = "0.1"
group = "org.spongepowered.downloads"

plugins {
    `java-library`
}


dependencies {
    api(platform(libs.jacksonBom))
    api(libs.bundles.serder)
    api(libs.maven)
    api(libs.vavr)
}

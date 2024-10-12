
version = "0.1"
group = "org.spongepowered.downloads"

plugins {
    id("io.micronaut.library")
}

micronaut {
    testRuntime("junit5")
}

dependencies {
    api(platform(libs.jacksonBom))
    api(libs.bundles.serder)

    // Annotation processor of validation kinds
    annotationProcessor(libs.bundles.validation.processors)

    // HTTP type validation
    api(libs.jakarta.validation)
    implementation(libs.micronaut.validation)


    testAnnotationProcessor("io.micronaut:micronaut-inject-java")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
}

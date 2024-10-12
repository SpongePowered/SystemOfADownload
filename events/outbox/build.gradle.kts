import io.micronaut.testresources.buildtools.KnownModules

plugins {
    id("com.github.johnrengelman.shadow")
    id("io.micronaut.library")
    id("io.micronaut.test-resources")
    id("io.micronaut.aot")
}

micronaut {

    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("org.spongepowered.downloads.outbox.*")
    }
    testResources {
        enabled.set(true)
//        sharedServer.set(true)
//        additionalModules.addAll(KnownModules.R2DBC_POSTGRESQL)
    }
}

dependencies {
    api(project(":events"))

    // Micronaut kafka dependency
    implementation("io.micronaut.kafka:micronaut-kafka")
    // Micronaut kafka annotation processor
    annotationProcessor("io.micronaut.kafka:micronaut-kafka")

    // databases
    runtimeOnly(libs.bundles.postgres.runtime)
    implementation(libs.bundles.postgres.r2dbc)
    annotationProcessor(libs.bundles.postgres.annotations)

    // Serder
    annotationProcessor(libs.bundles.serder.processor)
    implementation(libs.bundles.serder)

    // Validation
    annotationProcessor(libs.bundles.validation.processors)


    // Add micronaut test resources
    testImplementation(libs.bundles.micronaut.testresources)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.postgres.test)
    testRuntimeOnly(libs.bundles.junit.runtime)
    testResourcesService(libs.postgres.driver)

    compileOnly("org.graalvm.nativeimage:svm")
}

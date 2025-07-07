import io.micronaut.gradle.testresources.StartTestResourcesService
import io.micronaut.testresources.buildtools.KnownModules

plugins {
    id("io.micronaut.application")
    id("io.micronaut.test-resources")
    id("io.micronaut.aot")
    id("com.gradleup.shadow")
}


micronaut {

    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("org.spongepowered.downloads.artifacts.*")
    }
    testResources {
        enabled.set(true)
        sharedServer.set(true)
        additionalModules.addAll(KnownModules.JDBC_POSTGRESQL)
    }
}


application {
    mainClass.set("org.spongepowered.downloads.artifacts.server.Application")
}

tasks {
    test {
        useJUnitPlatform()
    }
}
tasks.withType<StartTestResourcesService>().configureEach {
    useClassDataSharing.set(false)
}

graalvmNative.toolchainDetection.set(false)

dependencies {
    implementation(project(":artifacts:api"))
    implementation(project(":artifacts:events"))
    implementation(project(":events:outbox"))

    annotationProcessor(libs.bundles.postgres.annotations)
    annotationProcessor(libs.bundles.validation.processors)
    annotationProcessor(libs.bundles.serder.processor)


    implementation("io.micronaut.validation:micronaut-validation")



    implementation(libs.bundles.serder)
    // Micronaut - HTTP
    implementation(libs.bundles.micronaut.http)
    // Validation
    implementation(libs.bundles.validation)

    // Git
    implementation(libs.bundles.git)

    runtimeOnly(libs.bundles.micronaut.runtime)
    runtimeOnly(libs.bundles.logging)

    implementation(libs.bundles.appSerder)

    // databases
    implementation(libs.bundles.postgres.jdbc)
    // Liquibase required jdbc driver
    runtimeOnly(libs.postgres.jdbc)



    testImplementation(libs.bundles.micronaut.testresources)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.postgres.test)
    testRuntimeOnly(libs.bundles.junit.runtime)
    testResourcesService(libs.postgres.driver)

    compileOnly("org.graalvm.nativeimage:svm")

}

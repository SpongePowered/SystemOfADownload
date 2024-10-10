import io.micronaut.gradle.testresources.StartTestResourcesService
import io.micronaut.testresources.buildtools.KnownModules

plugins {
    id("io.micronaut.application")
    id("io.micronaut.test-resources")
    id("io.micronaut.aot")
    id("com.github.johnrengelman.shadow")
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
        additionalModules.addAll(KnownModules.R2DBC_POSTGRESQL)
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
    implementation(project(":akka"))
    implementation(libs.vavr)
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut:micronaut-http-validation")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation(libs.bundles.git)

    runtimeOnly("org.yaml:snakeyaml")

    implementation(libs.bundles.appSerder)
    implementation(libs.bundles.akkaManagement)
    implementation(libs.bundles.actorsPersistence)

    // databases
    implementation("io.micronaut.data:micronaut-data-r2dbc")
    implementation("io.vertx:vertx-pg-client")
    runtimeOnly(libs.postgres.r2dbc)
    implementation("jakarta.persistence:jakarta.persistence-api:2.2.3")
    // Liquibase required jdbc driver
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("org.postgresql:postgresql")
    // Liquibase migrations
    implementation("io.micronaut.liquibase:micronaut-liquibase")

    // Micronaut
    implementation("io.micronaut:micronaut-http-client-jdk")
    implementation("io.micronaut:micronaut-management")

//    implementation(libs.lightbend.management)
//    implementation(libs.lightbend.bootstrap)
//    implementation(libs.akka.discovery)

    implementation(libs.akka.diagnostics)

    testImplementation("io.micronaut.testresources:micronaut-test-resources-extensions-junit-platform")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation(project(":akka:testkit"))
    testImplementation(libs.akka.persistence.testkit)
    testImplementation("org.testcontainers:r2dbc")
    testImplementation("org.testcontainers:postgresql")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testResourcesService("org.postgresql:postgresql")
    compileOnly("org.graalvm.nativeimage:svm")


    testImplementation("org.junit.jupiter:junit-jupiter-engine")


////    compileOnly("org.graalvm.nativeimage:svm")
//
//    implementation("io.micronaut:micronaut-validation")
}

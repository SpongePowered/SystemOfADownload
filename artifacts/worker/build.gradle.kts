import org.gradle.kotlin.dsl.version

plugins {
    id("com.github.johnrengelman.shadow")
    id("io.micronaut.minimal.application")
    id("io.micronaut.docker")
    id("io.micronaut.test-resources")
}


micronaut {

    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("org.spongepowered.downloads.worker.*")
    }
    testResources {
//        additionalModules.add("hibernate-reactive-postgresql")
//        sharedServer.set(true)
    }
}
dependencies {
    implementation(project(":artifacts:api"))
    implementation(project(":artifacts:events"))
    implementation(libs.vavr)

    // Jackson
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    annotationProcessor("io.micronaut.validation:micronaut-validation-processor")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")

    // validation

    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation(libs.jakarta.validation)

    annotationProcessor("io.micronaut.microstream:micronaut-microstream-annotations")
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.security:micronaut-security-annotations")
    // DB Access
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.data:micronaut-data-r2dbc")

    implementation("io.micronaut.liquibase:micronaut-liquibase")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.vertx:vertx-pg-client")
//    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation(platform(libs.akkaBom))
    implementation(libs.bundles.actors)
    implementation(libs.bundles.akkaManagement)
    implementation(libs.bundles.actorsPersistence)

    runtimeOnly("ch.qos.logback:logback-classic")
//    compileOnly("org.graalvm.nativeimage:svm")

//    implementation("io.micronaut:micronaut-validation")
}

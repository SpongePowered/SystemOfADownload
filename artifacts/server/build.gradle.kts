

val akkaVersion: String by project
val scalaVersion: String  by project
val akkaManagementVersion: String  by project
val akkaProjection: String by project
val vavr: String by project


tasks {
    dockerBuild {
        images.add("${project.name}:${project.version}")
    }
    dockerBuildNative {
        images.add("${project.name}:${project.version}")

    }
}
graalvmNative.toolchainDetection.set(false)
micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("systemofadownload.*")
    }
    testResources {
        additionalModules.add("r2dbc-postgresql")
    }
}
graalvmNative {
    binaries {
        named("main") {
            imageName.set("mn-graalvm-application")
            buildArgs("--verboase")
        }
    }
}

dependencies {
    implementation(project(":artifacts:api"))
    implementation("io.vavr:vavr:${vavr}")
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.openapi:micronaut-openapi")
    annotationProcessor("io.micronaut.security:micronaut-security-annotations")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("com.ongres.scram:client:2.1")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.data:micronaut-data-r2dbc")
    implementation("io.micronaut.liquibase:micronaut-liquibase")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.reactor:micronaut-reactor-http-client")
    implementation("io.micronaut.security:micronaut-security-ldap")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.toml:micronaut-toml")
    implementation("io.micronaut.xml:micronaut-jackson-xml")
    implementation("io.swagger.core.v3:swagger-annotations")
    implementation("io.vertx:vertx-pg-client")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation(platform("com.typesafe.akka:akka-bom_${scalaVersion}:${akkaVersion}"))
    implementation("com.typesafe.akka:akka-actor-typed_${scalaVersion}")
    implementation("com.typesafe.akka:akka-persistence-typed_${scalaVersion}")
    implementation("com.lightbend.akka:akka-projection-core_${scalaVersion}")
    implementation("com.typesafe.akka:akka-cluster-sharding-typed_${scalaVersion}")
    implementation("com.typesafe.akka:akka-cluster-typed_${scalaVersion}")
    implementation("com.typesafe.akka:akka-discovery_${scalaVersion}")
    implementation("com.typesafe.akka:akka-discovery_${scalaVersion}")
    implementation("com.lightbend.akka.management:akka-management_${scalaVersion}:${akkaManagementVersion}")
    implementation("com.lightbend.akka.management:akka-management-cluster-bootstrap_${scalaVersion}:${akkaManagementVersion}")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.postgresql:r2dbc-postgresql")
    compileOnly("org.graalvm.nativeimage:svm")

    implementation("io.micronaut:micronaut-validation")
}

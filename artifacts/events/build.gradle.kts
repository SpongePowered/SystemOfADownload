

plugins {
    id("com.gradleup.shadow")
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
    api(project(":artifacts:api"))
    api(project(":events:api"))
}




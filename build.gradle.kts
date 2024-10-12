import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel

version = "0.1"
group = "systemofadownload"

plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
    id("io.micronaut.library") version "4.4.3" apply false
    id("io.micronaut.application") version "4.4.3" apply false
    id("io.micronaut.docker") version "4.4.3" apply false
    id("io.micronaut.aot") version "4.4.3" apply false
    id("io.micronaut.test-resources") version "4.4.3" apply false
    id("org.gradlex.extra-java-module-info") version "1.9" apply false
}

repositories {
    mavenCentral()
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}


allprojects {
    apply(plugin = "java-library")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        if (JavaVersion.current() < JavaVersion.VERSION_21) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }
    }

    tasks {
//        withType<JavaCompile> {
//            options.compilerArgs.add("--enable-preview")
//        }
//        withType<Test> {
//            jvmArgs("--enable-preview")
//        }
    }

}





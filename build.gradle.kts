import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel

version = "0.1"
group = "systemofadownload"

plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.7" apply false
    id("io.micronaut.library") version "4.5.4" apply false
    id("io.micronaut.application") version "4.5.4" apply false
    id("io.micronaut.docker") version "4.5.4" apply false
    id("io.micronaut.aot") version "4.5.4" apply false
    id("io.micronaut.test-resources") version "4.5.4" apply false
    id("net.kyori.indra.licenser.spotless") version "3.1.3"
}

repositories {
    mavenCentral()
}
val organization: String by project
val projectUrl: String by project


tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

indraSpotlessLicenser {
    licenseHeaderFile(rootProject.file("HEADER.txt"))

    property("name", "Sponge")
    property("organization", organization)
    property("url", projectUrl)
}

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "net.kyori.indra.licenser.spotless")

    indraSpotlessLicenser {
        licenseHeaderFile(rootProject.file("HEADER.txt")) // default value
        property("name", "SystemOfADownload") // replace $name in the header file with the provided value
        property("organization", organization)
        property("url", projectUrl)

    }

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





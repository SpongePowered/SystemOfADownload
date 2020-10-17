plugins {
    // Apply the java plugin to add support for Java
    `java-library`

    // Apply the application plugin to add support for building a CLI application.
    application
    id("net.minecrell.licenser") version "0.4.1"
    checkstyle
    idea
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
}

dependencies {

    // Bootstrapper - literally just make this a webapp already...
    implementation("com.sparkjava:spark-core:2.8.0")

    // Language helpful features
    implementation("io.vavr:vavr:0.10.3")
    implementation("org.checkerframework:checker-qual:3.4.1")

    // GraphQL Requirements
    implementation("com.graphql-java:graphql-java:15.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.11.3")
    implementation("com.fasterxml.jackson.core:jackson-core:2.11.3")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.11.3")
    // And the vavr jackson compat module
    implementation("io.vavr:vavr-jackson:0.10.3")

    // JGit
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.9.0.202009080501-r")

    // our database
    implementation("org.postgresql:postgresql:42.2.18")

    // TESTING!!!
    testImplementation(platform("org.junit:junit-bom:5.7.0"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testImplementation("org.mockito:mockito-core:3.5.13")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(15))
    }
}

application {
    // Define the main class for the application.
    mainClassName = "org.spongepowered.downloads.App"
}

checkstyle {
    toolVersion = "8.36.2"
    configProperties["severity"] = "warning"
    configDirectory.set(project.projectDir.resolve("config").resolve("checkstyle"))
}
val organization: String by project
val url: String by project

license {
    header = project.file("HEADER.txt")
    ext {
        this["name"] = project.name
        this["organization"] = organization
        this["url"] = url
    }
    newLine = false
}

tasks {

    // https://stackoverflow.com/a/63457166/3032166
    withType<JavaCompile> {
        doFirst {
            options.compilerArgs.add("--module-path")
            options.compilerArgs.add(classpath.asPath)
        }
    }

    test {
        useJUnitPlatform()
    }
}
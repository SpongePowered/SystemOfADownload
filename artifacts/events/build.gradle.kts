

plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.toVersion("20")
    targetCompatibility = JavaVersion.toVersion("20")
}

dependencies {
    api(project(":artifacts:api"))
    api(project(":akka"))

}




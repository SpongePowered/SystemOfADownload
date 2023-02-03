
version = "0.1"
group = "org.spongepowered.downloads"


val akkaVersion: String by project
val scalaVersion: String  by project
val akkaManagementVersion: String  by project
val akkaProjection: String by project

dependencies {
    implementation("com.ongres.scram:client:2.1")
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation(platform("com.typesafe.akka:akka-bom_${scalaVersion}:${akkaVersion}"))
    implementation("com.typesafe.akka:akka-actor-typed_${scalaVersion}")
    implementation("com.typesafe.akka:akka-cluster-sharding-typed_${scalaVersion}")
    implementation("com.typesafe.akka:akka-cluster-typed_${scalaVersion}")
    implementation("com.typesafe.akka:akka-discovery_${scalaVersion}")
    implementation("com.typesafe.akka:akka-discovery_${scalaVersion}")
    implementation("com.lightbend.akka.management:akka-management_${scalaVersion}:${akkaManagementVersion}")
    implementation("com.lightbend.akka.management:akka-management-cluster-bootstrap_${scalaVersion}:${akkaManagementVersion}")

    runtimeOnly("ch.qos.logback:logback-classic")
    compileOnly("org.graalvm.nativeimage:svm")

    implementation("io.micronaut:micronaut-validation")
}



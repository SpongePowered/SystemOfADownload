import com.lightbend.lagom.core.LagomVersion
import com.typesafe.sbt.packager.docker.DockerChmodType
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{HeaderLicenseStyle, headerLicenseStyle}

import scala.sys.process.Process

ThisBuild / organization := "org.spongepowered"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.6"

// License setup
ThisBuild / organizationName := "SpongePowered"
ThisBuild / startYear := Some(2020)
ThisBuild / licenses += ("MIT", url("https://opensource.org/licenses/MIT"))

// Deployed Repositories
// TODO - Figure out deploying to our sonatype and to maven central
//    Then also figure out deploying docker images???

// Liquibase Docker Tasks
lazy val buildLiquibaseImage = taskKey[Unit]("Build the Liquibase docker image")
val rootFilter = ScopeFilter( inProjects( soadRoot ), inConfigurations(Compile))
buildLiquibaseImage := {
  val versionTag = version.all(rootFilter).value.head
  Process(Seq("docker", "build", "-t", s"spongepowered/systemofadownload-liquibase:$versionTag", "./liquibase/", "-f", "./liquibase/Dockerfile")).!
}

lazy val runLiquibase = taskKey[Unit]("Runs the liquibase migration against a local dev database")

lazy val setupDevEnvironment = taskKey[Unit]("Runs the necessary commands to set up a local environment to run the application")

lazy val setupPostgres = taskKey[Unit]("Runs a postgres instance for local development")
lazy val setupKafka = taskKey[Unit]("Runs Kafka and zookeeper instance for local development")
setupDevEnvironment := {
  setupPostgres.value
  runLiquibase.value
  setupKafka.value
}
setupPostgres := {
  Process(Seq("sh",
    s"${soadRoot.base.absolutePath}/dev/run_postgres.sh",
  )).!
}

setupKafka := {
  Process(Seq("sh", s"${soadRoot.base.absolutePath}/dev/run_kafka.sh")).!
}

runLiquibase := {
  Process(Seq("docker",
    "run",
    "--rm",
    "--mount", s"type=bind,source=${soadRoot.base.absolutePath}/liquibase/changelog,target=/liquibase/changelog,readonly",
    "--network=host",
    "liquibase/liquibase",
    "--logLevel=info",
    s"--url=jdbc:postgresql://localhost:5432/default",
    "--defaultsFile=/liquibase/changelog/liquibase.properties",
    "--changeLogFile=changelog.xml",
    "--classpath=/liquibase/changelog",
    "--username=admin",
    "--password=password",
    "update")).!
}

// region dependency versions

lazy val vavrVersion = "0.10.3"
lazy val vavr = "io.vavr" % "vavr" % vavrVersion
lazy val vavrJackson = "io.vavr" % "vavr-jackson" % vavrVersion

lazy val pac4jVersion = "3.7.0"
lazy val pac4jHttp = "org.pac4j" % "pac4j-http" % pac4jVersion
lazy val pac4jJwt = "org.pac4j" % "pac4j-jwt" % pac4jVersion

lazy val lagomPac4jVersion = "2.2.1"
lazy val lagomPac4j = "org.pac4j" %% "lagom-pac4j" % lagomPac4jVersion

lazy val junitVersion = "5.7.2" // Enable Junit5
lazy val junit = "org.junit.jupiter" % "junit-jupiter-api" % junitVersion % Test
lazy val jupiterInterfaceVersion = "0.9.1" // sbt-jupiter-interface
lazy val jupiterInterface = "net.aichler" % "jupiter-interface" % jupiterInterfaceVersion % Test


lazy val jacksonVersion = "2.11.4" // this is tied to play's jackson version
lazy val jacksonDataformatXml = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % jacksonVersion

lazy val akkaStreamTyped = "com.typesafe.akka" %% "akka-stream-typed" % LagomVersion.akka
lazy val akkaPersistenceTestkit = "com.typesafe.akka" %% "akka-persistence-testkit" % LagomVersion.akka % Test
val AkkaManagementVersion = "1.1.1"
lazy val akkaKubernetesDiscovery = "com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % AkkaManagementVersion

lazy val hibernate = "org.hibernate" % "hibernate-core" % "5.5.4.Final"
lazy val postgres = "org.postgresql" % "postgresql" % "42.2.18"

lazy val guice = "com.google.inject" % "guice" % "5.0.1"

lazy val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.0.202109080827-r"
lazy val jgit_jsch = "org.eclipse.jgit" % "org.eclipse.jgit.ssh.jsch" % "5.13.0.202109080827-r"

// endregion

// region - project blueprints

def soadProject(name: String) =
  Project(name, file(name)).settings(
    moduleName := s"systemofadownload-$name",
    Compile / javacOptions := Seq("--release", "16", "-parameters", "-encoding", "UTF-8"), //Override the settings Lagom sets
    artifactName := { (_: ScalaVersion, module: ModuleID, artifact: Artifact) =>
      s"${artifact.name}-${module.revision}.${artifact.extension}"
    },
    Compile / doc / sources := Seq.empty,
    Compile / packageDoc / publishArtifact := false,
    compileOrder := CompileOrder.JavaThenScala, //Needed so scalac doesn't try to parse the files
    headerLicense := Some(HeaderLicense.Custom(
      """This file is part of SystemOfADownload, licensed under the MIT License (MIT).
        |
        |Copyright (c) SpongePowered <https://spongepowered.org/>
        |Copyright (c) contributors
        |
        |Permission is hereby granted, free of charge, to any person obtaining a copy
        |of this software and associated documentation files (the "Software"), to deal
        |in the Software without restriction, including without limitation the rights
        |to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        |copies of the Software, and to permit persons to whom the Software is
        |furnished to do so, subject to the following conditions:
        |
        |The above copyright notice and this permission notice shall be included in
        |all copies or substantial portions of the Software.
        |
        |THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        |IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        |FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        |AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        |LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        |OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
        |THE SOFTWARE.
        |""".stripMargin
    )),
    headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax,
    headerEmptyLine := false

  )

def apiSoadProject(name: String) =
  soadProject(name).settings(
    libraryDependencies ++= Seq(
      // Lagom Java API
      lagomJavadslApi,
      lagomJavadslJackson,
      //Language Features
      vavr,
      // Override guice from Lagom to support Java 16
      guice
    )
  )

def serverSoadProject(name: String) =
  soadProject(name).enablePlugins(LagomJava, DockerPlugin).settings(
    libraryDependencies ++= Seq(
      //Lagom Dependencies
      // Specifically set up Akka-Clustering
      lagomJavadslCluster,
      // Set up Discovery between Services
      lagomJavadslAkkaDiscovery,
      akkaKubernetesDiscovery,
      // I mean, we are a server, aren't we?
      lagomJavadslServer,
      // Set up logging
      lagomLogback,
      //Language Features for Serialization/Deserialization
      vavrJackson,
      // Override guice from Lagom to support Java 16
      guice,
      //Test Dependencies
      lagomJavadslTestKit,
      junit, // Always enable Junit 5
      jupiterInterface
    )
  ).settings(
    dockerUpdateLatest := true,
    dockerBaseImage := "openjdk:16-slim@sha256:fc8e7ca99badf28dfd5f061bca882e7a333bde59d8fed7dc87f5e16dfe6bc0cf",
    dockerChmodType := DockerChmodType.UserGroupWriteExecute,
    dockerExposedPorts := Seq(9000,8558, 2552),
    dockerLabels ++= Map(
      "author" -> "spongepowered"
    ),
    Docker / maintainer := "spongepowered",
    Docker / packageName := s"systemofadownload-$name",
    dockerUsername := Some("spongepowered"),
    Universal / javaOptions ++= Seq(
      "-Dpidfile.path=/dev/null"
    )
  )

def implSoadProject(name: String, implFor: Project) =
  serverSoadProject(name).dependsOn(
    //The service we're implementing
    implFor
  ).settings(
    libraryDependencies ++= Seq(
      // We use kafka for all inter-service message forwarding
      lagomJavadslKafkaBroker
    )
  )

def implSoadProjectWithPersistence(name: String, implFor: Project) =
  implSoadProject(name, implFor).settings(
    libraryDependencies ++= Seq(
      //Lagom Database Dependencies
      lagomJavadslPersistenceJpa,
      //Database Dependencies
      hibernate,
      postgres,
      akkaPersistenceTestkit
    )
  )

// endregion

// region Project Definitions

lazy val `artifact-api` = apiSoadProject("artifact-api").settings(
  //Maven Dependency for Version Parsing
  libraryDependencies += "org.apache.maven" % "maven-artifact" % "3.8.1"
)
lazy val `artifact-impl` = implSoadProjectWithPersistence("artifact-impl", `artifact-api`).dependsOn(
  //Inter module dependencies
  `server-auth`,
  `sonatype`
).settings(
  libraryDependencies += jgit
)

lazy val `artifact-query-api` = apiSoadProject("artifact-query-api").dependsOn(
  //Inter module dependencies
  `artifact-api`
)
lazy val `artifact-query-impl` = implSoadProjectWithPersistence("artifact-query-impl", `artifact-query-api`)

lazy val `versions-api` = apiSoadProject("versions-api").dependsOn(
  //Module Dependencies
  `artifact-api`
)

lazy val `versions-impl` = implSoadProjectWithPersistence("versions-impl", `versions-api`).dependsOn(
  //Other SystemOfADownload Common Implementation Dependencies
  `server-auth`
).settings(
  libraryDependencies += jgit,
  libraryDependencies += jgit_jsch
)

lazy val `versions-query-api` = apiSoadProject("versions-query-api").dependsOn(
  //Inter module dependencies
  `artifact-api`
)
lazy val `versions-query-impl` = implSoadProjectWithPersistence("versions-query-impl", `versions-query-api`)

lazy val `version-synchronizer` = serverSoadProject("version-synchronizer").dependsOn(
  //Modules we consume
  `versions-api`,
  `server-auth`,
  `sonatype`,
  `artifact-api`
).settings(
  libraryDependencies ++= Seq(
    //Lagom Dependencies
    // We use kafka for all inter-service message forwarding
    lagomJavadslKafkaBroker,
    // Use persistence
    lagomJavadslPersistenceJpa,
    // Use Akka-Typed Streams
    akkaStreamTyped,
    //Database Dependencies
    hibernate,
    postgres,
    //XML Deserialization - to interpret Maven's metadata.xml
    jacksonDataformatXml
  )
)

lazy val `sonatype` = soadProject( "sonatype").settings(
  libraryDependencies ++= Seq(
    //Language Features
    vavr,
    //Jackson Serialization
    "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    //Test Dependencies
    junit,
    jupiterInterface,
    jacksonDataformatXml % Test,
    vavrJackson % Test exclude("com.fasterxml.jackson.core", "jackson-databind")
  )
)

lazy val `auth-api` = apiSoadProject("auth-api").settings(
  //Auth Dependency
  libraryDependencies ++= Seq(
    lagomPac4j,
    pac4jHttp,
    pac4jJwt
  )
)
lazy val `auth-impl` = serverSoadProject("auth-impl").dependsOn(
  //The service we're implementing
  `auth-api`,
  //Server Authentication Dependency
  `server-auth`
).settings(
  //LDAP dependency
  libraryDependencies += "org.pac4j" % "pac4j-ldap" % pac4jVersion
)

lazy val `server-auth` = soadProject("server-auth").dependsOn(`auth-api`).settings(
  libraryDependencies ++= Seq(
    //Language Features
    vavr,
    //Lagom Server Dependency
    lagomJavadslServer
  )
)

// This service is literally acting as our reverse proxy gateway, so it will
// perform routing of API calls that are inherited from the "public api" calls
// versus the "internal" api calls. This service is primarily what will be
// public facing, but not interacted with between the other services.
lazy val `gateway-api` = apiSoadProject("gateway-api").dependsOn(
  `artifact-api`,
  `artifact-query-api`,
  `versions-api`,
  `versions-query-api`,
  `auth-api`,
).settings(
  libraryDependencies ++= Seq(
    lagomPac4j,
    pac4jHttp,
    pac4jJwt
  )
)

lazy val `gateway-impl` = serverSoadProject("gateway-impl").dependsOn(
  //The service we're implementing
  `gateway-api`,
  //Server Authentication Dependency
  `server-auth`
)

// endregion

lazy val soadRoot = project.in(file(".")).settings(
  name := "SystemOfADownload"
).aggregate(
  `artifact-api`,
  `artifact-impl`,
  `artifact-query-api`,
  `artifact-query-impl`,
  `versions-api`,
  `versions-impl`,
  `versions-query-api`,
  `versions-query-impl`,
  `version-synchronizer`,
  `sonatype`,
  `auth-api`,
  `auth-impl`,
  `server-auth`,
)

ThisBuild / lagomCassandraEnabled := false
ThisBuild / lagomKafkaEnabled := false
ThisBuild / lagomKafkaPort := 9092
ThisBuild /lagomServicesPortRange := PortRange(21000, 23000)


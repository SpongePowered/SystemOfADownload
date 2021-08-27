import com.lightbend.lagom.core.LagomVersion
import xsbti.compile

ThisBuild / organization := "org.spongepowered"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.6"

ThisBuild / organizationName := "SpongePowered"
ThisBuild / startYear := None
ThisBuild / licenses += "MIT" -> url("http://opensource.org/licenses/MIT")

lazy val vavrVersion = "0.10.3"
lazy val vavr = "io.vavr" % "vavr" % vavrVersion
lazy val vavrJackson = "io.vavr" % "vavr-jackson" % vavrVersion

lazy val pac4jVersion = "3.7.0"
lazy val pac4jHttp = "org.pac4j" % "pac4j-http" % pac4jVersion
lazy val pac4jJwt = "org.pac4j" % "pac4j-jwt" % pac4jVersion

lazy val lagomPac4jVersion = "2.2.1"
lazy val lagomPac4j = "org.pac4j" %% "lagom-pac4j" % lagomPac4jVersion

lazy val lagomOpenApiVersion = "1.3.1" //TODO: Check what it actually is
lazy val lagomOpenApiJavaApi = "org.taymyr.lagom" % "lagom-openapi-java-api" % lagomOpenApiVersion
lazy val lagomOpenApiJavaImpl = "org.taymyr.lagom" % "lagom-openapi-java-impl" % lagomOpenApiVersion
lazy val swaggerVersion = "2.1.10" //TODO: Check what it actually is
lazy val swaggerAnnotations = "io.swagger.core.v3" % "swagger-annotations" % swaggerVersion

lazy val junitVersion = "5.7.2"
lazy val junit = "org.junit.jupiter" % "junit-jupiter-api" % junitVersion % Test

lazy val jacksonVersion = "2.10.4"
lazy val jacksonDataformatXml = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-xml" % jacksonVersion

lazy val akkaStreamTyped = "com.typesafe.akka" %% "akka-stream-typed" % LagomVersion.akka
lazy val akkaPersistenceTestkit = "com.typesafe.akka" %% "akka-persistence-testkit" % LagomVersion.akka % Test

lazy val hibernate = "org.hibernate" % "hibernate-core" % "5.5.4.Final"
lazy val postgres = "org.postgresql" % "postgresql" % "42.2.18"

lazy val guice = "com.google.inject" % "guice" % "5.0.1"

def soadProject(name: String) =
  Project(name, file(name)).enablePlugins(LagomJava).settings(
    moduleName := s"systemofadownload-$name",
    Compile / javacOptions := Seq("--release", "16", "-parameters"), //Override the settings Lagom sets
    compileOrder := CompileOrder.JavaThenScala //Needed so scalac doesn't try to parse the files
  )

def apiSoadProject(name: String) =
  soadProject(name).settings(
    //Language Features
    libraryDependencies += vavr
  )

def implSoadProject(name: String, implFor: Project) =
  soadProject(name).enablePlugins(LagomPlugin).dependsOn(
    //The service we're implementing
    implFor
  ).settings(
    libraryDependencies ++= Seq(
      //Lagom Dependencies
      lagomJavadslPersistenceJpa,
      lagomLogback,
      //Database Dependencies
      hibernate,
      postgres,
      //Language Features
      vavrJackson,
      //Test Dependencies
      lagomJavadslTestKit
    )
  )

lazy val `artifact-api` = apiSoadProject("artifact-api").settings(
  //Maven Dependency for Version Parsing
  libraryDependencies += "org.apache.maven" % "maven-artifact" % "3.8.1"
)
lazy val `artifact-impl` = implSoadProject("artifact-impl", `artifact-api`).dependsOn(
  //Inter module dependencies
  `server-auth`,
  `sonatype`
).settings(
  libraryDependencies ++= Seq(
    //Lagom Dependencies
    lagomJavadslKafkaBroker,
    //Java 16 related dependency bumps
    guice,
    //Auth
    lagomPac4j,
    //Test Dependencies
    akkaPersistenceTestkit,
    junit
  )
)

lazy val `artifact-query-api` = apiSoadProject("artifact-query-api").dependsOn(
  //Inter module dependencies
  `artifact-api`
)
lazy val `artifact-query-impl` = implSoadProject("artifact-query-impl", `artifact-query-api`)
  .enablePlugins(LagomAkkaHttpServer)
  .settings(
    //Lagom Dependencies
    libraryDependencies += lagomJavadslServer
  )

lazy val `versions-api` = apiSoadProject("versions-api").dependsOn(
  //Module Dependencies
  `artifact-api`
)
lazy val `versions-impl` = implSoadProject("versions-impl", `versions-api`).dependsOn(
  //Other SystemOfADownload Common Implementation Dependencies
  `server-auth`
).settings(
  libraryDependencies ++= Seq(
    //Lagom Dependencies
    lagomJavadslKafkaBroker,
    //Java 16 bumped dependency
    guice,
    //Test dependencies
    junit
  )
)

lazy val `versions-query-api` = apiSoadProject("versions-query-api").dependsOn(
  //Inter module dependencies
  `artifact-api`
)
lazy val `versions-query-impl` = implSoadProject("versions-query-impl", `versions-query-api`)
  .enablePlugins(LagomAkkaHttpServer)
  .settings(
    libraryDependencies ++= Seq(
      //Lagom Dependencies
      lagomLog4j2,
      lagomJavadslServer,
      lagomJavadslKafkaBroker,
    )
)

lazy val `version-synchronizer` = soadProject("version-synchronizer").enablePlugins(LagomPlayJava).dependsOn(
  //Modules we consume
  `versions-api`,
  `server-auth`,
  `sonatype`,
  `artifact-api`
).settings(
  libraryDependencies ++= Seq(
    //Lagom Dependencies
    lagomJavadslPersistenceJpa,
    lagomJavadslKafkaBroker,
    lagomLogback,
    akkaStreamTyped,
    //Database Dependencies
    hibernate,
    postgres,
    //Java 16 bumped dependency
    guice,
    //Language features
    vavrJackson,
    //Auth
    lagomPac4j,
    //XML Deserialization
    jacksonDataformatXml,
    //Test Dependencies
    lagomJavadslTestKit,
    junit
  )
)

lazy val `sonatype` = project.settings(
  moduleName := "systemofadownload-sonatype",
  Compile / javacOptions ++= Seq("--release", "16", "-parameters"),
  compileOrder := CompileOrder.JavaThenScala, //Needed so scalac doesn't try to parse the files
  libraryDependencies ++= Seq(
    //Language Features
    vavr,
    //Jackson Serialization
    "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
    //Test Dependencies
    junit,
    jacksonDataformatXml % Test,
    vavrJackson % Test exclude("com.fasterxml.jackson.core", "jackson-databind")
  )
)

lazy val `auth-api` = apiSoadProject("auth-api").settings(
  //Auth Dependency
  libraryDependencies += lagomPac4j
)
lazy val `auth-impl` = implSoadProject("auth-impl", `auth-api`).dependsOn(
  //Server Authentication Dependency
  `server-auth`,
).settings(
  //LDAP dependency
  libraryDependencies += "org.pac4j" % "pac4j-ldap" % pac4jVersion
)
lazy val `server-auth` = soadProject("server-auth").enablePlugins(LagomAkkaHttpServer).settings(
  libraryDependencies ++= Seq(
    //Language Features
    vavr,
    //Pac4J dependencies
    lagomPac4j,
    pac4jHttp,
    pac4jJwt,
    //Lagom Server Dependency
    lagomJavadslServer
  )
)

lazy val soadRoot = project.in(file(".")).aggregate(
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
  `server-auth`
)

/*
//TODO: Where to put these settings?
lagomCassandraEnabled := false
lagomCassandraPort := 9042
lagomServiceLocatorEnabled := true
lagomKafkaPort := 9092
lagomKafkaZookeeperPort := 2181
lagomKafkaEnabled := false
*/

enablePlugins(LicenseHeaderCheck)
licenseHeaderFile := file("HEADER.txt")
licenseHeaderProperties := Map(
  "name" -> "SystemOfADownload",
  "organization" -> "SpongePowered",
  "url" -> "https://spongepowered.org/"
)

def inDirectoryFilter(directory: File): FileFilter = {
  val canonical = directory.getCanonicalPath
  new SimpleFileFilter(_.getCanonicalPath.startsWith(canonical))
}

licenseHeaderCheckFiles / excludeFilter :=
  "README*" &&
    inDirectoryFilter(file("src/test/resources")) &&
    inDirectoryFilter(file("src/main/resources")) &&
    inDirectoryFilter(file(".run")) &&
    "*.xml" &&
    "*.yaml" &&
    "*.yml" &&
    ".editorconfig" &&
    "LICENSE.txt" &&
    inDirectoryFilter(file("config")) &&
    "*.conf" &&
    inDirectoryFilter(file("terraform")) &&
    ".java-version"

//Faster, faster (No idea if pipelining works with Java)
ThisBuild / turbo := true
ThisBuild / usePipelining := true

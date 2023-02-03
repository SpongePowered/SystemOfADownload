

val akkaVersion: String by project
val scalaVersion: String  by project
val akkaManagementVersion: String  by project
val akkaProjection: String by project

subprojects {
    dependencies {
        implementation(project(":akka"))
    }
}
dependencies {

}




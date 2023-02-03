
val jacksonVersion:String by project
dependencies {
    api(platform("com.fasterxml.jackson:jackson-bom:${jacksonVersion}"))
    api("com.fasterxml.jackson:jackson-core")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")

}

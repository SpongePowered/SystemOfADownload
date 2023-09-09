
dependencyResolutionManagement {

    versionCatalogs {
        create("akka") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

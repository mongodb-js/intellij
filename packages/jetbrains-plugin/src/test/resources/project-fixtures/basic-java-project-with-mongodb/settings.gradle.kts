rootProject.name = "basic-java-project-with-mongodb"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../../../../../../gradle/libs.versions.toml")) // full path to the versions toml
        }
    }
}

rootProject.name = "mongodb-jetbrains-plugin"

pluginManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        mavenCentral()
        gradlePluginPortal()
    }
}

include(
    "packages:mongodb-mql-model",
    "packages:mongodb-dialects",
    "packages:mongodb-autocomplete-engine",
    "packages:mongodb-linting-engine",
    "packages:mongodb-access-adapter",
    "packages:jetbrains-plugin",
)
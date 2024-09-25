rootProject.name = "mongodb-jetbrains-plugin"

include(
    "packages:mongodb-mql-model",
    "packages:mongodb-dialects",
    "packages:mongodb-dialects:java-driver",
    "packages:mongodb-dialects:spring-criteria",
    "packages:mongodb-dialects:mongosh",
    "packages:mongodb-autocomplete-engine",
    "packages:mongodb-linting-engine",
    "packages:mongodb-access-adapter",
    "packages:mongodb-access-adapter:datagrip-access-adapter",
    "packages:jetbrains-plugin",
)

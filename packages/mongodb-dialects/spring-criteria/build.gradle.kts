plugins {
    id("com.mongodb.intellij.plugin-component")
}

dependencies {
    implementation(project(":packages:mongodb-mql-model"))
    implementation(project(":packages:mongodb-dialects"))
    implementation(project(":packages:mongodb-dialects:java-driver"))
    implementation(libs.snakeyaml)
}

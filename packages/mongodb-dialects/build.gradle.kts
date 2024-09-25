plugins {
    id("com.mongodb.intellij.isolated-module")
}

dependencies {
    implementation(project(":packages:mongodb-mql-model"))
}

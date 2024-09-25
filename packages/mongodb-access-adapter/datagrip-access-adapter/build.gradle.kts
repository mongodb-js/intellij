plugins {
    id("com.mongodb.intellij.plugin-component")
}

dependencies {
    implementation(libs.gson)
    implementation(libs.owasp.encoder)
    implementation(libs.mongodb.driver)
    implementation(libs.bson.kotlin)

    implementation(project(":packages:mongodb-access-adapter"))
    implementation(project(":packages:mongodb-mql-model"))
    implementation(project(":packages:mongodb-dialects"))
    implementation(project(":packages:mongodb-dialects:mongosh"))
}

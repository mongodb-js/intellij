plugins {
    id("com.mongodb.intellij.isolated-module")
}

dependencies {
    implementation(libs.bson.kotlin)
    implementation(libs.owasp.encoder)
    implementation(libs.semver.parser)
}

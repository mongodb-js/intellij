dependencies {
    implementation(libs.owasp.encoder)
    implementation(libs.semver.parser)

    implementation(project(":packages:mongodb-mql-model"))
    implementation(project(":packages:mongodb-dialects"))
}

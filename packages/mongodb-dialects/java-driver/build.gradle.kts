plugins {
    id("com.mongodb.intellij.plugin-component")
}

dependencies {
    implementation(project(":packages:mongodb-mql-model"))
    implementation(project(":packages:mongodb-dialects"))

    testImplementation(project(":packages:mongodb-mql-model"))
    testImplementation(libs.mongodb.driver)
    testImplementation(libs.testing.intellij.testingFramework) {
        exclude("ai.grazie.spell")
        exclude("ai.grazie.utils")
        exclude("ai.grazie.nlp")
        exclude("ai.grazie.model")
        exclude("org.jetbrains.teamcity")
    }
}

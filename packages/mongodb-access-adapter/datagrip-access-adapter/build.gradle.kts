repositories {
    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

plugins {
    alias(libs.plugins.intellij)
}

tasks {
    named("test", Test::class) {
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        val homePath =
            project.layout.buildDirectory
                .dir("idea-sandbox/config-test")
                .get()
                .asFile.absolutePath

        jvmArgs(
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
                "-Dpolyglot.engine.WarnInterpreterOnly=false",
                "-Dpolyglot.log.level=OFF",
                "-Didea.home.path=$homePath",
            ),
        )
    }
}

intellij {
    version.set(libs.versions.intellij.min) // Target IDE Version
    type.set(libs.versions.intellij.type) // Target IDE Platform

    plugins.set(listOf("com.intellij.java", "com.intellij.database"))
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

    testImplementation("com.jetbrains.intellij.platform:test-framework-junit5:241.15989.155") {
        exclude("ai.grazie.spell")
        exclude("ai.grazie.utils")
        exclude("ai.grazie.nlp")
        exclude("ai.grazie.model")
        exclude("org.jetbrains.teamcity")
    }

    testImplementation(libs.testing.testContainers.core)
    testImplementation(libs.testing.testContainers.jupiter)
    testImplementation(libs.testing.testContainers.mongodb)
}

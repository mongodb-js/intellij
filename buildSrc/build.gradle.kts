repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    api(libs.buildScript.plugin.kotlin)
    api(libs.buildScript.plugin.versions)
    api(libs.buildScript.plugin.spotless)
    api(libs.buildScript.plugin.testRetry)
    api(libs.buildScript.plugin.intellij.plugin)
    api(libs.buildScript.plugin.intellij.changelog)
    api(libs.buildScript.plugin.jmh)
    api(libs.buildScript.plugin.jmhreport)
    api(libs.buildScript.plugin.diktat)
    api(libs.buildScript.plugin.ktlint)
}

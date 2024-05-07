repositories {
    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

plugins {
    alias(libs.plugins.intellij)
    alias(libs.plugins.jmh)
}

intellij {
    version.set(libs.versions.intellij.min) // Target IDE Version
    type.set(libs.versions.intellij.type) // Target IDE Platform

    plugins.set(listOf("com.intellij.java", "com.intellij.database"))
}

dependencies {
    implementation(project(":packages:mongodb-access-adapter"))
    implementation(project(":packages:mongodb-autocomplete-engine"))
    implementation(project(":packages:mongodb-dialects"))
    implementation(project(":packages:mongodb-linting-engine"))
    implementation(project(":packages:mongodb-mql-model"))

    implementation(libs.segment)

    jmh(libs.kotlin.stdlib)
    jmh(libs.testing.jmh.core)
    jmh(libs.testing.jmh.annotationProcessor)
    jmh(libs.testing.jmh.generatorByteCode)

    testImplementation(libs.testing.intellij.ideImpl)
    testImplementation(libs.testing.intellij.coreUi)

    testImplementation(libs.testing.remoteRobot)
    testImplementation(libs.testing.remoteRobotDeps.remoteFixtures)
    testImplementation(libs.testing.remoteRobotDeps.okHttp)
    testImplementation(libs.testing.remoteRobotDeps.retrofit)
    testImplementation(libs.testing.remoteRobotDeps.retrofitGson)
}

jmh {
    timeUnit.set("ms")
    benchmarkMode.set(listOf("ss"))
    batchSize.set(100)
    failOnError.set(false)
    forceGC.set(true)
    humanOutputFile.set(rootProject.layout.buildDirectory.file("reports/jmh/human.txt"))
    resultsFile.set(rootProject.layout.buildDirectory.file("reports/jmh/results.json"))
    resultFormat.set("json")
    profilers.set(listOf("gc"))
}

tasks {
    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("241.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

tasks.test {
    jvmArgs = listOf(
        "--add-opens=java.base/java.lang=ALL-UNNAMED"
    )
}
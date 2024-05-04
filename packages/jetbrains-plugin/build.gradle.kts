import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.ChangelogSectionUrlBuilder
import org.jetbrains.changelog.date

repositories {
    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

plugins {
    alias(libs.plugins.intellij)
    alias(libs.plugins.jmh)
    alias(libs.plugins.changelog)
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
    benchmarkMode.set(listOf("sample"))
    iterations.set(10)
    timeOnIteration.set("6s")
    timeUnit.set("ms")

    warmup.set("10s")
    warmupIterations.set(3)
    warmupMode.set("INDI")
    fork.set(1)
    threads.set(1)
    failOnError.set(false)
    forceGC.set(true)

    humanOutputFile.set(rootProject.layout.buildDirectory.file("reports/jmh/human.txt"))
    resultsFile.set(rootProject.layout.buildDirectory.file("reports/jmh/results.json"))
    resultFormat.set("json")
    profilers.set(listOf("gc"))

    zip64.set(true)
}

tasks {
    patchPluginXml {
        sinceBuild.set("231")
        untilBuild.set("241.*")
        version.set(rootProject.libs.versions.our.plugin)

        changeNotes.set(provider {
            changelog.renderItem(
                changelog
                    .getUnreleased()
                    .withHeader(false)
                    .withEmptySections(false),
                Changelog.OutputType.HTML
            )
        })
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

changelog {
    version.set(rootProject.libs.versions.our.plugin)
    path.set(rootProject.file("CHANGELOG.md").canonicalPath)
    header.set(provider { "[${version.get()}] - ${date()}" })
    headerParserRegex.set("""(\d+\.\d+.\d+)""".toRegex())
    introduction.set(
        """
        MongoDB Plugin that does a lot of features. This is markdown.
        """.trimIndent()
    )
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
    lineSeparator.set("\n")
    combinePreReleases.set(true)
    sectionUrlBuilder.set(ChangelogSectionUrlBuilder { repositoryUrl, currentVersion, previousVersion, isUnreleased -> "foo" })
}
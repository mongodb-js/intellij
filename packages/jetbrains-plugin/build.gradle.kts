import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.date
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask

repositories {
    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

plugins {
    alias(libs.plugins.intellij)
    alias(libs.plugins.jmh)
    alias(libs.plugins.jmhreport)
    alias(libs.plugins.changelog)
}

intellij {
    version.set(libs.versions.intellij.min) // Target IDE Version
    type.set(libs.versions.intellij.type) // Target IDE Platform

    plugins.set(listOf("com.intellij.java", "com.intellij.database"))
}

dependencies {
    implementation(project(":packages:mongodb-access-adapter"))
    implementation(project(":packages:mongodb-access-adapter:datagrip-access-adapter"))
    implementation(project(":packages:mongodb-autocomplete-engine"))
    implementation(project(":packages:mongodb-dialects"))
    implementation(project(":packages:mongodb-dialects:java-driver"))
    implementation(project(":packages:mongodb-dialects:spring-data-query-annotation"))
    implementation(project(":packages:mongodb-linting-engine"))
    implementation(project(":packages:mongodb-mql-model"))

    implementation(libs.mongodb.driver)
    implementation(libs.segment)

    jmh(libs.kotlin.stdlib)
    jmh(libs.testing.jmh.core)
    jmh(libs.testing.jmh.annotationProcessor)
    jmh(libs.testing.jmh.generatorByteCode)

    testCompileOnly(libs.testing.intellij.ideImpl)
    testCompileOnly(libs.testing.intellij.coreUi)

    testImplementation(libs.testing.jsoup)
    testImplementation(libs.testing.video.recorder)
    testImplementation(libs.testing.remoteRobot)
    testImplementation(libs.testing.remoteRobotDeps.remoteFixtures)
    testImplementation(libs.testing.remoteRobotDeps.ideLauncher)
    testImplementation(libs.testing.remoteRobotDeps.okHttp)
    testImplementation(libs.testing.remoteRobotDeps.retrofit)
    testImplementation(libs.testing.remoteRobotDeps.retrofitGson)
}

jmh {
    benchmarkMode.set(listOf("thrpt"))
    iterations.set(10)
    timeOnIteration.set("6s")
    timeUnit.set("s")

    warmup.set("1s")
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

jmhReport {
    jmhResultPath = rootProject.layout.buildDirectory.file("reports/jmh/results.json").get().asFile.absolutePath
    jmhReportOutput = rootProject.layout.buildDirectory.dir("reports/jmh/").get().asFile.absolutePath
}

tasks {
    register("buildProperties", WriteProperties::class) {
        group = "build"

        destinationFile.set(project.layout.projectDirectory.file("src/main/resources/build.properties"))
        property("pluginVersion", rootProject.version)
        property("segmentApiKey", System.getenv("BUILD_SEGMENT_API_KEY") ?: "<none>")
    }

    named("test", Test::class) {
        useJUnitPlatform {
            excludeTags("UI")
        }
    }

    register("uiTest", Test::class) {
        group = "verification"
        useJUnitPlatform {
            includeTags("UI")
        }

        dependsOn("instrumentTestCode")
    }

    named("runIdeForUiTests", RunIdeForUiTestTask::class) {
        systemProperties(
            mapOf(
                "jb.consents.confirmation.enabled" to false,
                "jb.privacy.policy.text" to "<!--999.999-->",
                "eap.require.license" to true,
                "ide.mac.message.dialogs.as.sheets" to false,
                "ide.mac.file.chooser.native" to false,
                "jbScreenMenuBar.enabled" to false,
                "apple.laf.useScreenMenuBar" to false,
                "idea.trust.all.projects" to true,
                "ide.show.tips.on.startup.default.value" to false,
                "idea.is.internal" to true,
                "robot-server.port" to "8082",
            ),
        )
    }

    downloadRobotServerPlugin {
        version.set(libs.versions.intellij.remoteRobot)
    }

    withType<ProcessResources> {
        dependsOn("buildProperties")
    }

    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("242.*")
        version.set(rootProject.version.toString())

        changeNotes.set(
            provider {
                changelog.renderItem(
                    changelog
                        .getUnreleased()
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            },
        )
    }

    signPlugin {
        certificateChain.set(System.getenv("JB_CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("JB_PRIVATE_KEY"))
        password.set(System.getenv("JB_PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        channels =
            when (System.getenv("JB_PUBLISH_CHANNEL")) {
                "ga" -> listOf("Stable")
                "beta" -> listOf("beta")
                else -> listOf("eap")
            }
        token.set(System.getenv("JB_PUBLISH_TOKEN"))
    }
}

changelog {
    version.set(rootProject.version.toString())
    path.set(rootProject.file("CHANGELOG.md").canonicalPath)
    header.set(provider { "[${version.get()}] - ${date()}" })
    headerParserRegex.set("""(\d+\.\d+.\d+)""".toRegex())
    introduction.set(
        """
        MongoDB plugin for IntelliJ IDEA.
        """.trimIndent(),
    )
    itemPrefix.set("-")
    keepUnreleasedSection.set(true)
    unreleasedTerm.set("[Unreleased]")
    groups.set(listOf("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"))
    lineSeparator.set("\n")
    combinePreReleases.set(true)
}

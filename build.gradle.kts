import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "com.mongodb"
// This should be bumped when releasing a new version using the versionBump task:
// ./gradlew versionBump -Pmode={major,minor,patch}
version="0.0.1"

plugins {
    alias(libs.plugins.versions)
}

buildscript {
    repositories {
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath(libs.buildScript.plugin.kotlin)
        classpath(libs.buildScript.plugin.ktlint)
        classpath(libs.buildScript.plugin.versions)
        classpath(libs.buildScript.plugin.spotless)
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.ben-manes.versions")
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
    }

    dependencies {
        val testImplementation by configurations
        val compileOnly by configurations

        compileOnly(rootProject.libs.kotlin.stdlib)
        testImplementation(rootProject.libs.testing.jupiter.engine)
        testImplementation(rootProject.libs.testing.jupiter.vintage.engine)
        testImplementation(rootProject.libs.testing.mockito.core)
        testImplementation(rootProject.libs.testing.mockito.kotlin)
    }

    tasks {
        withType<JavaCompile> {
            sourceCompatibility = "17"
            targetCompatibility = "17"
        }

        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "17"
        }

        withType<Test> {
            useJUnitPlatform()
        }
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            diktat().configFile(rootProject.layout.projectDirectory.file("gradle/diktat.yml").asFile.absolutePath)
        }
    }
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/reports"
    reportfileName = "dependencyUpdates"
}

tasks {
    register("test") {
        dependsOn(
            subprojects.filter { it.project.name != ":packages:jetbrains-plugin"}.map {
                it.tasks["test"]
            }
        )
    }

    register("versionBump") {
        group = "my tasks"
        description = "Increments the version of the plugin."

        fun generateVersion(): String {
            val updateMode = rootProject.findProperty("mode") ?: "patch"
            val (oldMajor, oldMinor, oldPatch) = rootProject.version.toString().split(".").map(String::toInt)
            var (newMajor, newMinor, newPatch) = arrayOf(oldMajor, oldMinor, 0)

            when (updateMode) {
                "major" -> newMajor = (oldMajor + 1).also { newMinor = 0 }
                "minor" -> newMinor = (oldMinor + 1)
                else -> newPatch = oldPatch + 1
            }
            return "$newMajor.$newMinor.$newPatch"
        }
        doLast {
            val newVersion = rootProject.findProperty("exactVersion") ?: generateVersion()
            val oldContent = buildFile.readText()
            val newContent = oldContent.replace("""="$version"""", """="$newVersion"""")
            buildFile.writeText(newContent)
        }
    }

    register("gitHooks") {
        exec {
            rootProject.file(".git/hooks").mkdirs()
            commandLine("cp", "./gradle/pre-commit", "./.git/hooks")
        }
    }

    register("getVersion") {
        doLast {
            println(rootProject.version)
        }
    }
}
import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.cyclonedx.gradle.CycloneDxTask

group = "com.mongodb"
version = libs.versions.our.plugin

plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.cyclonedx)
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
        classpath(libs.buildScript.plugin.cyclonedx)
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "com.github.ben-manes.versions")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "org.cyclonedx.bom")

    repositories {
        mavenCentral()
    }

    dependencies {
        val testImplementation by configurations
        val compileOnly by configurations

        compileOnly(rootProject.libs.kotlin.stdlib)
        testImplementation(rootProject.libs.testing.jupiter)
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

tasks.named<CycloneDxTask>("cyclonedxBom").configure {
    setIncludeConfigs(listOf("runtimeClasspath"))
    setProjectType("application")
    setSchemaVersion("1.5")
    setDestination(project.file("build/reports"))
    setOutputName("cyclonedx-sbom")
    setOutputFormat("json")
    setIncludeLicenseText(true)
}

tasks {
    register("test") {
        dependsOn(
            subprojects.filter { it.project.name != ":packages:jetbrains-plugin"}.map {
                it.tasks["test"]
            }
        )
    }

    register("functionalTests") {
        dependsOn(
            project(":packages:jetbrains-plugin").tasks["test"]
        )
    }

    register("performanceTest") {
        dependsOn(
            project(":packages:jetbrains-plugin").tasks["jmh"]
        )
    }

    register("gitHooks") {
        exec {
            rootProject.file(".git/hooks").mkdirs()
            commandLine("cp", "./gradle/pre-commit", "./.git/hooks")
        }
    }
}
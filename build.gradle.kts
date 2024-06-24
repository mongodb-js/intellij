import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

group = "com.mongodb"
// This should be bumped when releasing a new version using the versionBump task:
// ./gradlew versionBump -Pmode={major,minor,patch}
version = "0.0.1"

plugins {
    alias(libs.plugins.versions)
    id("jacoco")
}

buildscript {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath(libs.buildScript.plugin.kotlin)
        classpath(libs.buildScript.plugin.versions)
        classpath(libs.buildScript.plugin.spotless)
    }
}

val kotlinMultiplatform =
    arrayOf(
        ":packages:typescript-bindings",
        ":packages:mongodb-autocomplete-engine",
        ":packages:mongodb-dialects",
        ":packages:mongodb-dialects:javascript-ejson",
        ":packages:mongodb-linting-engine",
        ":packages:mongodb-mql-model",
    )

subprojects {
    repositories {
        mavenCentral()
    }

    if (!kotlinMultiplatform.contains(project.path)) {
        apply(plugin = "java")
        apply(plugin = "org.jetbrains.kotlin.jvm")
        apply(plugin = "jacoco")

        apply(plugin = "com.github.ben-manes.versions")
        apply(plugin = "com.diffplug.spotless")

        dependencies {
            val testImplementation by configurations
            val compileOnly by configurations

            configurations.named("runtimeClasspath").configure {
                exclude("org.jetbrains.kotlin")
                exclude("org.jetbrains.kotlinx")
            }

            compileOnly(rootProject.libs.kotlin.stdlib)
            compileOnly(rootProject.libs.kotlin.coroutines.core)
            compileOnly(rootProject.libs.kotlin.reflect)
            testImplementation(rootProject.libs.testing.jupiter.engine)
            testImplementation(rootProject.libs.testing.jupiter.vintage.engine)
            testImplementation(rootProject.libs.testing.mockito.core)
            testImplementation(rootProject.libs.testing.mockito.kotlin)
            testImplementation(rootProject.libs.kotlin.coroutines.test)
        }

        tasks {
            withType<JavaCompile> {
                sourceCompatibility = "17"
                targetCompatibility = "17"
            }

            withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_17
                }
            }

            withType<Test> {
                useJUnitPlatform()

                extensions.configure(JacocoTaskExtension::class) {
                    isJmx = true
                    includes = listOf("com.mongodb.*")
                    isIncludeNoLocationClasses = true
                }

                jacoco {
                    toolVersion = "0.8.12"
                    isScanForTestClasses = true
                }

                jvmArgs(
                    listOf(
                        "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    ),
                )
            }

            withType<JacocoReport> {
                reports {
                    xml.required = true
                    csv.required = false
                    html.outputLocation = layout.buildDirectory.dir("reports/jacocoHtml")
                }

                executionData(
                    files(withType(Test::class.java)).filter { it.name.endsWith(".exec") && it.exists() },
                )
            }
        }

        configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            kotlin {
                diktat()
                    .configFile(
                        rootProject.layout.projectDirectory
                            .file("gradle/diktat.yml")
                            .asFile.absolutePath,
                    )
            }
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
    register("unitTest") {
        group = "verification"
        dependsOn(
            subprojects
                .filter {
                    it.project.name != "jetbrains-plugin" &&
                        it.project.name != "packages"
                }.map {
                    it.tasks["test"]
                },
        )
    }

    register("versionBump") {
        group = "versioning"
        description = "Increments the version of the plugin."

        fun generateVersion(): String {
            val updateMode = rootProject.findProperty("mode") ?: "patch"
            val (oldMajor, oldMinor, oldPatch) =
                rootProject.version
                    .toString()
                    .split(".")
                    .map(String::toInt)
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
        group = "environment"
        exec {
            rootProject.file(".git/hooks").mkdirs()
            commandLine("cp", "./gradle/pre-commit", "./.git/hooks")
        }
    }

    register("getVersion") {
        group = "environment"
        doLast {
            println(rootProject.version)
        }
    }
}

import groovy.json.JsonSlurper
import java.net.URL

group = "com.mongodb"
// This should be bumped when releasing a new version using the versionBump task:
// ./gradlew versionBump -Pmode={major,minor,patch}
version = "0.0.1"

plugins {
    base
    id("com.github.ben-manes.versions")
    id("jacoco-report-aggregation")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    jacocoAggregation(project(":packages:jetbrains-plugin"))
    jacocoAggregation(project(":packages:mongodb-access-adapter"))
    jacocoAggregation(project(":packages:mongodb-access-adapter:datagrip-access-adapter"))
    jacocoAggregation(project(":packages:mongodb-autocomplete-engine"))
    jacocoAggregation(project(":packages:mongodb-dialects"))
    jacocoAggregation(project(":packages:mongodb-dialects:java-driver"))
    jacocoAggregation(project(":packages:mongodb-dialects:mongosh"))
    jacocoAggregation(project(":packages:mongodb-dialects:spring-criteria"))
    jacocoAggregation(project(":packages:mongodb-dialects:spring-@query"))
    jacocoAggregation(project(":packages:mongodb-linting-engine"))
    jacocoAggregation(project(":packages:mongodb-mql-model"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testType = TestSuiteType.UNIT_TEST
        }
    }
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("testCodeCoverageReport"))
}

tasks {
    register("unitTest") {
        group = "verification"
        dependsOn(
            subprojects.filter {
                it.project.name != "jetbrains-plugin" &&
                        it.project.name != "packages"
            }.map {
                it.tasks["test"]
            }
        )
    }

    register("versionBump") {
        group = "versioning"
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
            val newContent = oldContent.replace(""" = "$version"""", """ = "$newVersion"""")
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

    register("mainStatus") {
        group = "environment"

        doLast {
            val checks = JsonSlurper().parse(URL("https://api.github.com/repos/mongodb-js/intellij/commits/main/check-runs")) as Map<String, Any>
            val check_runs = checks["check_runs"] as List<Map<String, Any>>
            var success: Boolean = true
            for (check in check_runs) {
                if (check["name"] == "Prepare Release") {
                    continue
                }

                if (check["conclusion"] != "success") {
                    System.err.println("[❌] Check ${check["name"]} is still with status ${check["status"]} and conclusion ${check["conclusion"]}: ${check["html_url"]}")
                    success = false
                } else {
                    println("[✅] Check ${check["name"]} has finished successfully: ${check["html_url"]}")
                }
            }

            if (!success) {
                throw GradleException("Checks in main must be successful.")
            }
        }
    }
}

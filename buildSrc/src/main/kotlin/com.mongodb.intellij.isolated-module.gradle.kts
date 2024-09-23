import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.support.delegates.TaskContainerDelegate.*
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm")
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint")
}

repositories {
    mavenCentral()
}

val libs = the<LibrariesForLibs>()

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.coroutines.core)
    compileOnly(libs.kotlin.reflect)
    testImplementation(libs.testing.jupiter.engine)
    testImplementation(libs.testing.jupiter.params)
    testImplementation(libs.testing.jupiter.vintage.engine)
    testImplementation(libs.testing.mockito.core)
    testImplementation(libs.testing.mockito.kotlin)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.testing.testContainers.core)
    testImplementation(libs.testing.testContainers.jupiter)
    testImplementation(libs.testing.testContainers.mongodb)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = libs.versions.java.target.get()
        targetCompatibility = libs.versions.java.target.get()
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = libs.versions.java.target.get()
    }

    withType<Test> {
        useJUnitPlatform()

        extensions.configure(JacocoTaskExtension::class) {
            isJmx = true
            includes = listOf("com.mongodb.*")
            isIncludeNoLocationClasses = true
        }

        jacoco {
            toolVersion = libs.versions.jacoco.get()
            isScanForTestClasses = true
        }

        jvmArgs(
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED"
            )
        )
    }

    withType<JacocoReport> {
        reports {
            xml.required = true
            csv.required = false
            html.outputLocation = layout.buildDirectory.dir("reports/jacocoHtml")
        }

        executionData(
            files(withType(Test::class.java)).filter { it.name.endsWith(".exec") && it.exists() }
        )
    }
}

configure<KtlintExtension> {
    version.set("1.3.1")
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    enableExperimentalRules.set(true)

    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }

    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}


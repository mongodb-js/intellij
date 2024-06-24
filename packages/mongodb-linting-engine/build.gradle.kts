import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":packages:mongodb-mql-model"))
            implementation(rootProject.libs.kotlin.stdlib)
            implementation(rootProject.libs.kotlin.coroutines.core)
            implementation(rootProject.libs.kotlin.reflect)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    js(IR) {
        moduleName = project.name

        nodejs {
        }

        binaries.library()
        generateTypeScriptDefinitions()
    }
}

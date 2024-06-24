plugins {
    kotlin("multiplatform")
}

kotlin {
    sourceSets {
        jsMain.dependencies {
            implementation(rootProject.libs.kotlin.stdlib)
            implementation(rootProject.libs.kotlin.coroutines.core)
            implementation(rootProject.libs.kotlin.reflect)

            implementation(project(":packages:mongodb-mql-model"))
            implementation(project(":packages:mongodb-dialects"))
            implementation(project(":packages:mongodb-dialects:javascript-ejson"))
            implementation(project(":packages:mongodb-linting-engine"))
            implementation(project(":packages:mongodb-autocomplete-engine"))
        }

        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    js(IR) {
        moduleName = "mongodb-smarty"

        nodejs {
        }

        binaries.library()

        generateTypeScriptDefinitions()
    }
}

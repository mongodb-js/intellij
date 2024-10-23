plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.mongodb.driver)
    implementation(libs.testing.spring.mongodb)
}

tasks.test {
    useJUnitPlatform()
}

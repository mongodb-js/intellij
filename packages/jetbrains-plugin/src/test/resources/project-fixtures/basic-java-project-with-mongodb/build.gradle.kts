plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.mongodb:mongodb-driver-sync:5.1.3")
    implementation("org.springframework.data:spring-data-mongodb:4.3.2")
}

tasks.test {
    useJUnitPlatform()
}

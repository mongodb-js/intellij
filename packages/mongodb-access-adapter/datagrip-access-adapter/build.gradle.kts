repositories {
    maven("https://www.jetbrains.com/intellij-repository/releases/")
}
dependencies {
    implementation(libs.gson)
    implementation(libs.mongodb.driver)
    implementation(project(":packages:mongodb-access-adapter"))

    compileOnly(libs.testing.intellij.ideImpl)
    compileOnly(libs.testing.intellij.coreUi)
    compileOnly("com.jetbrains.intellij.database:database-sql:241.15989.150")
    compileOnly("com.jetbrains.intellij.database:database-connectivity:241.15989.150")
    compileOnly("com.jetbrains.intellij.database:database-core-impl:241.15989.150") {
        exclude("com.jetbrains.fus.reporting", "ap-validation")
    }
    compileOnly("com.jetbrains.intellij.database:database-jdbc-console:241.15989.150")
    compileOnly("com.jetbrains.intellij.database:database:241.15989.150")
    compileOnly("com.jetbrains.intellij.platform:images:241.15989.157")
    compileOnly("com.jetbrains.intellij.grid:grid-impl:241.15989.150")
    compileOnly("com.jetbrains.intellij.grid:grid:241.15989.150")
    compileOnly("com.jetbrains.intellij.grid:grid-core-impl:241.15989.150")
}
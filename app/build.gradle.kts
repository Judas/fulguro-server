plugins {
    id("fulgurogo-module")
    alias(libs.plugins.shadow.jar)
    application
}

application {
    mainClass.set(providers.gradleProperty("fulgurogo.main.class").get())
}

/**
 * Run from the repository root, not from `app/`.
 *
 * `ssh.private.key.file` in config.properties is a path relative to the root, and JavaExec would otherwise resolve it
 * against this subproject — so the debug SSH tunnel died on a FileNotFoundException, the pool never reached MySQL and
 * every service stopped on its first tick. IntelliJ already runs from the root, which is why the failure only ever
 * showed up from Gradle.
 */
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:discord"))

    implementation(project(":modules:kgs"))
    implementation(project(":modules:ogs"))

    implementation(project(":modules:gold"))
    implementation(project(":modules:fgc"))
    implementation(project(":modules:house"))
    implementation(project(":modules:api"))

    implementation(project(":modules:ping"))
    implementation(project(":modules:clean"))

    implementation(libs.gson)
    implementation(libs.hikari)
    implementation(libs.javalin)
    implementation(libs.jda)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.mysql.connector)
    implementation(libs.sl4j)
    implementation(libs.sql2o)
}

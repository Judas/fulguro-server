plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.noarg)
    alias(libs.plugins.shadow.jar)
    application
}

repositories {
    mavenCentral()
    maven(url = uri("https://plugins.gradle.org/m2/"))
    maven(url = uri("https://jitpack.io"))
    maven(url = uri("https://m2.dv8tion.net/releases"))
    gradlePluginPortal()
}

application {
    group = providers.gradleProperty("fulgurogo.group.name").get()
    version = providers.gradleProperty("fulgurogo.version.name").get()
    mainClass.set(providers.gradleProperty("fulgurogo.main.class").get())
}

kotlin {
    jvmToolchain(providers.gradleProperty("fulgurogo.java.version").get().toInt())
}

noArg {
    annotation("com.fulgurogo.common.utilities.GenerateNoArgConstructor")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(project(":modules:discord"))

    implementation(project(":modules:kgs"))
    implementation(project(":modules:ogs"))
    implementation(project(":modules:fox"))
    implementation(project(":modules:igs"))
    implementation(project(":modules:ffg"))
    implementation(project(":modules:egf"))

    implementation(project(":modules:gold"))
    implementation(project(":modules:fgc"))
    implementation(project(":modules:api"))

    implementation(project(":modules:ping"))
    implementation(project(":modules:clean"))

    implementation(libs.commons.net)
    implementation(libs.gson)
    implementation(libs.hikari)
    implementation(libs.javalin)
    implementation(libs.jda)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.mysql.connector)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.sl4j)
    implementation(libs.sql2o)
}

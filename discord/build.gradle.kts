plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.noarg)
    `java-library`
}

group = providers.gradleProperty("fulgurogo.group.name").get()
version = providers.gradleProperty("fulgurogo.version.name").get()

repositories {
    mavenCentral()
    maven(url = uri("https://plugins.gradle.org/m2/"))
    maven(url = uri("https://jitpack.io"))
    maven(url = uri("https://m2.dv8tion.net/releases"))
    gradlePluginPortal()
}

kotlin {
    jvmToolchain(providers.gradleProperty("fulgurogo.java.version").get().toInt())
}

noArg {
    annotation("com.fulgurogo.common.utilities.GenerateNoArgConstructor")
}

dependencies {
    implementation(project(":common"))

    implementation(libs.jda)
    implementation(platform(libs.kotlin.bom))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.sql2o)
}

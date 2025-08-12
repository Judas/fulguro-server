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
    api(libs.hikari)
    api(libs.jsch)
    api(platform(libs.kotlin.bom))
    api(libs.kotlinx.coroutines)
    api(libs.kotlin.stdlib)
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.okhttp.urlconnection)
    api(libs.sql2o)
}

/**
 * Shared configuration for every module under `modules/` and for `app`.
 *
 * Plugin versions come from buildSrc's own dependencies rather than from the version catalog: the `plugins` block of a
 * precompiled script plugin cannot read `libs`. Keep buildSrc/build.gradle.kts in sync when bumping Kotlin.
 */
plugins {
    kotlin("jvm")
    kotlin("plugin.noarg")
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

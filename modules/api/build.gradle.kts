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
    implementation(project(":modules:discord"))
    implementation(project(":modules:kgs"))
    implementation(project(":modules:ogs"))
    implementation(project(":modules:fox"))
    implementation(project(":modules:igs"))
    implementation(project(":modules:ffg"))
    implementation(project(":modules:egf"))
    implementation(project(":modules:gold"))
    implementation(project(":modules:fgc"))

    implementation(libs.gson)
    implementation(libs.javalin)
}

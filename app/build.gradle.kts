plugins {
    id("fulgurogo-module")
    alias(libs.plugins.shadow.jar)
    application
}

application {
    mainClass.set(providers.gradleProperty("fulgurogo.main.class").get())
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
    implementation(libs.sl4j)
    implementation(libs.sql2o)
}

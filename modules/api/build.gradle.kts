plugins {
    id("fulgurogo-module")
}

dependencies {
    implementation(project(":modules:discord"))
    implementation(project(":modules:kgs"))
    implementation(project(":modules:ogs"))
    implementation(project(":modules:gold"))
    implementation(project(":modules:fgc"))
    implementation(project(":modules:fox"))
    implementation(project(":modules:house"))
    implementation(project(":modules:league"))
    // For CleanDatabaseAccessor.purgePlayer: the one place that already owns "every trace of a player".
    implementation(project(":modules:clean"))

    implementation(libs.gson)
    implementation(libs.javalin)

    testImplementation(kotlin("test"))
}

plugins {
    id("fulgurogo-module")
}

dependencies {
    implementation(project(":modules:discord"))
    implementation(project(":modules:kgs"))
    implementation(project(":modules:ogs"))
    implementation(project(":modules:gold"))
    implementation(project(":modules:fgc"))
    implementation(project(":modules:house"))

    implementation(libs.gson)
    implementation(libs.javalin)
}

plugins {
    id("fulgurogo-module")
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

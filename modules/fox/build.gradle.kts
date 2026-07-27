plugins {
    id("fulgurogo-module")
}

dependencies {
    implementation(project(":modules:discord"))
    implementation(libs.gson)
}

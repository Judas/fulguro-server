plugins {
    id("fulgurogo-module")
}

dependencies {
    implementation(project(":modules:common"))
    implementation(libs.gson)
}

plugins {
    id("fulgurogo-module")
}

dependencies {
    api(project(":modules:common"))
    api(libs.jda)
}

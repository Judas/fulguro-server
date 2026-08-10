plugins {
    id("fulgurogo-module")
}

dependencies {
    implementation(project(":modules:discord"))
    implementation(project(":modules:house"))
    implementation(project(":modules:gold"))
    implementation(project(":modules:ogs"))
}

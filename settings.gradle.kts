plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = providers.gradleProperty("fulgurogo.project.name").get()
include("app")
include("common")
include("kgs")

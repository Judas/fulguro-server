plugins {
    id("fulgurogo-module")
}

dependencies {
    api(libs.hikari)
    api(libs.jsch)
    api(libs.jsoup)
    api(platform(libs.kotlin.bom))
    api(libs.kotlinx.coroutines)
    api(libs.kotlin.stdlib)
    api(platform(libs.okhttp.bom))
    api(libs.okhttp)
    api(libs.okhttp.urlconnection)
    api(libs.sql2o)
}

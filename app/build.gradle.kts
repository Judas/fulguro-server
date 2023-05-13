plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.10"
    id("com.github.johnrengelman.shadow") version "8.1.0"
    application
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://m2.dv8tion.net/releases")
    }
}

dependencies {
    // Kotlin
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    // Discord
    implementation("net.dv8tion:JDA:4.4.0_352")

    // Javalin
    implementation("io.javalin:javalin:5.3.2")

    // Database
    implementation("mysql:mysql-connector-java:8.0.32")
    implementation("org.sql2o:sql2o:1.6.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // I/O
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:okhttp-urlconnection")
    implementation("commons-net:commons-net:3.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.jcraft:jsch:0.1.55")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.6")
}

application {
    group = "com.fulgurogo"
    version = "6.5"
    mainClass.set("com.fulgurogo.AppKt")
}

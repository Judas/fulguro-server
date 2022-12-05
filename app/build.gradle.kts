plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
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
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.2")

    // Discord
    implementation("net.dv8tion:JDA:4.4.0_352")

    // Javalin
    implementation("io.javalin:javalin:4.6.4")

    // Database
    implementation("mysql:mysql-connector-java:8.0.30")
    implementation("org.sql2o:sql2o:1.6.0")
    implementation("com.zaxxer:HikariCP:5.0.1")

    // I/O
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.10.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:okhttp-urlconnection")
    implementation("commons-net:commons-net:3.8.0")
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("com.jcraft:jsch:0.1.55")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.0")
}

application {
    group = "com.fulgurogo"
    version = "5.19"
    mainClass.set("com.fulgurogo.AppKt")
}

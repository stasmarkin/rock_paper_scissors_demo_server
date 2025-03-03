plugins {
    kotlin("jvm") version "2.1.10"
    application
}

group = "me.stasmarkin.rockpaperscissors"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.netty:netty-all:4.1.118.Final")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")
    
    // Guice
    implementation("com.google.inject:guice:7.0.0")
    implementation("com.google.inject.extensions:guice-assistedinject:7.0.0")
    
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

application {
    mainClass.set("me.stasmarkin.rockpaperscissors.MainKt")
} 
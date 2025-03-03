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

tasks.jar {
    manifest {
        attributes["Main-Class"] = "me.stasmarkin.rockpaperscissors.MainKt"
    }
    
    // This ensures that all dependencies are included in the jar
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })
}

// Add stress test task
tasks.register<JavaExec>("stressTest") {
    group = "application"
    description = "Run the stress test simulation"
    
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("me.stasmarkin.rockpaperscissors.StressTestKt")
    
    jvmArgs = listOf(
        "-Xms512m",                        // Initial heap size
        "-Xmx2g",                          // Maximum heap size
        "-XX:+UseG1GC",                    // Use G1 Garbage Collector
        "-Dio.netty.leakDetection.level=disabled", // Disable Netty leak detection
        "-Djava.net.preferIPv4Stack=true"  // Use IPv4 instead of IPv6
    )
    
}
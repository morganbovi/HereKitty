plugins {
    kotlin("jvm") version "2.0.21"
    id("com.gradleup.shadow") version "8.3.5"
}

val ktorVersion = "2.3.12"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets-jvm:$ktorVersion")

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.auth0:java-jwt:4.5.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "io.sweatshop.herekitty.relay.RelayServerKt"
    }
}

tasks.register<JavaExec>("runServer") {
    group = "herekitty"
    description = "Runs the relay server (a dumb byte pipe between two paired WebSocket clients)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.sweatshop.herekitty.relay.RelayServerKt")
}

tasks.register<JavaExec>("runDesktopClient") {
    group = "herekitty"
    description = "Runs the desktop-side test client: local TCP :6520 <-> relay WebSocket."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.sweatshop.herekitty.relay.DesktopBridgeClientKt")
    standardInput = System.`in`
    if (project.hasProperty("args")) {
        args((project.property("args") as String).split(" "))
    }
}

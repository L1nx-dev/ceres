plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    application
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.jline:jline:3.26.3")
    implementation("org.jsoup:jsoup:1.18.1")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
application { mainClass = "ceres.main.MainKt" }

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.register<JavaExec>("runGui") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ceres.gui.MainKt"
}

import org.gradle.api.file.DuplicatesStrategy

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.1"
    application
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.46.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.jline:jline:3.26.3")
    implementation("org.jsoup:jsoup:1.18.1")

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "ceres.main.MainKt"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.register<JavaExec>("runGui") {
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "ceres.gui.desktop.MainKt"
}

tasks.register("inspectDist") {
    doLast {
        val task = tasks.named<Sync>("installDist").get()
        task.inputs.files.files
            .filter { it.name == "runtime-desktop-1.12.1.jar" }
            .forEach { println(it.absolutePath) }
    }
}


tasks.named<Sync>("installDist") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

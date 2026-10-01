plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}
repositories { mavenCentral() }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    implementation("org.jline:jline:3.26.3")
    testImplementation(kotlin("test"))
}
kotlin { jvmToolchain(21) }
application { mainClass = "ceres.main.MainKt" }

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

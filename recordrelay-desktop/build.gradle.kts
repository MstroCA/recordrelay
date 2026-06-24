plugins {
    `java-library`
    application
    alias(libs.plugins.javafx)
}

description = "RecordRelay Desktop — JavaFX standalone application"

application {
    mainClass.set("io.recordrelay.desktop.RecordRelayApp")
}

javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.controls", "javafx.fxml")
}

tasks.processResources {
    filesMatching("io/recordrelay/desktop/app.properties") {
        expand("version" to project.version)
    }
}

// Use the arm64 Oracle JDK for the run task on Apple Silicon so JavaFX
// native libs (mac-aarch64) match the JVM architecture.
tasks.named<JavaExec>("run") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(23))
            vendor.set(JvmVendorSpec.matching("Oracle"))
        },
    )
    // Allow ES2 (Metal/OpenGL) with software fallback — prevents
    // QuantumRenderer "no suitable pipeline found" on some macOS setups.
    // sw = software renderer, bypasses Metal/OpenGL entirely — guaranteed to work on any Mac.
    jvmArgs("-Dprism.order=sw", "-Djava.awt.headless=false")
}

dependencies {
    implementation(project(":recordrelay-core"))
    implementation(project(":recordrelay-domain"))
    implementation(project(":recordrelay-cli"))
    implementation(project(":recordrelay-engine"))
    implementation(libs.atlantafx.base)
    implementation(libs.ikonli.javafx)
    implementation(libs.ikonli.mdi2)
    implementation(libs.jackson.databind)
    compileOnly(libs.spotbugs.annotations)
    runtimeOnly(libs.logback.classic)

    runtimeOnly(project(":recordrelay-adapter-postgres"))
    runtimeOnly(project(":recordrelay-adapter-mongodb"))
    runtimeOnly(project(":recordrelay-adapter-mysql"))
    runtimeOnly(project(":recordrelay-adapter-sqlserver"))
    runtimeOnly(project(":recordrelay-adapter-oracle"))
    runtimeOnly(project(":recordrelay-adapter-sqlite"))
    runtimeOnly(project(":recordrelay-adapter-cassandra"))
    runtimeOnly(project(":recordrelay-adapter-redis"))
    runtimeOnly(project(":recordrelay-adapter-elasticsearch"))
    runtimeOnly(project(":recordrelay-adapter-file"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testfx.core)
    testImplementation(libs.testfx.junit5)
}

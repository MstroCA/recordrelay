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
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.web")
}

// On Apple Silicon with only an x86_64 JDK 21 available, use the arm64 JDK 23
// for the run task so JavaFX native libs match the JVM architecture.
tasks.named<JavaExec>("run") {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(23))
            vendor.set(JvmVendorSpec.matching("Oracle"))
        })
}

dependencies {
    implementation(project(":recordrelay-core"))
    implementation(project(":recordrelay-domain"))
    implementation(project(":recordrelay-cli"))
    implementation(project(":recordrelay-engine"))
    implementation(libs.atlantafx.base)
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

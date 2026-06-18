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

dependencies {
    implementation(project(":core"))
    implementation(project(":cli"))
    implementation(project(":engine-batch"))
    implementation(libs.atlantafx.base)
    implementation(libs.jackson.databind)
    compileOnly(libs.spotbugs.annotations)
    runtimeOnly(libs.logback.classic)

    runtimeOnly(project(":mapping-parsers"))
    runtimeOnly(project(":connectors:connector-postgresql"))
    runtimeOnly(project(":connectors:connector-mongodb"))
    runtimeOnly(project(":connectors:connector-mysql"))
    runtimeOnly(project(":connectors:connector-sqlserver"))
    runtimeOnly(project(":connectors:connector-oracle"))
    runtimeOnly(project(":connectors:connector-sqlite"))
    runtimeOnly(project(":connectors:connector-cassandra"))
    runtimeOnly(project(":connectors:connector-redis"))
    runtimeOnly(project(":connectors:connector-elasticsearch"))
    runtimeOnly(project(":connectors:connector-file"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testfx.core)
    testImplementation(libs.testfx.junit5)
}

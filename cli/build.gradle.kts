plugins {
    `java-library`
    application
    alias(libs.plugins.shadow)
}

description = "RecordRelay CLI — Picocli-based command line interface"

application {
    mainClass.set("io.recordrelay.cli.RecordRelayCli")
}

dependencies {
    implementation(project(":core"))
    implementation(libs.picocli)
    implementation(libs.jackson.databind)
    implementation(libs.snakeyaml)
    compileOnly(libs.spotbugs.annotations)
    runtimeOnly(libs.logback.classic)

    implementation(project(":engine-clone"))

    // Connectors discovered at runtime via ServiceLoader
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
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testImplementation(libs.assertj.core)
}

tasks.shadowJar {
    archiveBaseName = "rr-cli"
    archiveClassifier = ""
    // Merge SPI descriptors so ServiceLoader-based connectors are discoverable in the fat JAR
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

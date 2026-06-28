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
    implementation(project(":recordrelay-core"))
    implementation(libs.picocli)
    implementation(libs.jackson.databind)
    implementation(libs.snakeyaml)
    implementation(libs.hikaricp)
    compileOnly(libs.spotbugs.annotations)
    runtimeOnly(libs.logback.classic)

    implementation(project(":recordrelay-engine"))
    implementation(project(":recordrelay-synthetic-engine"))

    // Connectors discovered at runtime via ServiceLoader
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
    runtimeOnly(project(":recordrelay-adapter-snowflake"))
    runtimeOnly(project(":recordrelay-adapter-dynamodb"))
    runtimeOnly(project(":recordrelay-adapter-bigquery"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testImplementation(libs.assertj.core)
}

tasks.shadowJar {
    archiveBaseName = "rr-cli"
    archiveClassifier = ""
    isZip64 = true
    // Merge SPI descriptors so ServiceLoader-based connectors are discoverable in the fat JAR.
    mergeServiceFiles()
    // dnsjava ships as a multi-release jar: its InetAddressResolverProvider is only under
    // META-INF/versions/18/. Without Multi-Release:true in the manifest the JVM cannot find
    // the class and throws a fatal ServiceConfigurationError before any connection is opened.
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Multi-Release"] = "true"
    }
}

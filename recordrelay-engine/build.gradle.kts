plugins {
    `java-library`
}

description = "RecordRelay Engine — clone orchestration, identity mapping, record fetch/write, replay"

dependencies {
    api(project(":recordrelay-core"))
    api(project(":recordrelay-domain"))
    api(project(":recordrelay-graph-engine"))
    api(project(":recordrelay-masking-engine"))
    api(project(":recordrelay-package-engine"))
    implementation(libs.hikaricp)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.slf4j.api)
    compileOnly(libs.spotbugs.annotations)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)

    // Integration tests (Testcontainers) exercise the satellite sync end-to-end against real
    // PostgreSQL databases. The postgres adapter is added at test runtime so ConnectorRegistry's
    // ServiceLoader can discover it, just like the production classpath.
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(project(":recordrelay-adapter-postgres"))
}

// Unit tests exclude @Tag("integration") — fast, no Docker required.
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// Integration tests run separately against real containers (requires Docker).
tasks.register<Test>("integrationTest") {
    description = "Runs Testcontainers integration tests (requires Docker)."
    group = "verification"
    useJUnitPlatform {
        includeTags("integration")
    }
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.test)
}

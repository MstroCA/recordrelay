plugins {
    `java-library`
}

description = "RecordRelay PostgreSQL connector — DataSourceConnector + SchemaInspector adapter"

dependencies {
    api(project(":core"))
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
}

// Adapter code can only be meaningfully tested against a real DB.
// Coverage is enforced by the integrationTest task, not unit tests.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    enabled = false
}

// Unit tests exclude @Tag("integration") — fast, no Docker required
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// Integration tests run separately against real containers
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

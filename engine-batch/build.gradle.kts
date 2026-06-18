plugins {
    `java-library`
}

description = "RecordRelay Spring Batch engine — chunk-oriented pipeline with checkpoint/restart support"

dependencies {
    api(project(":core"))
    implementation(libs.spring.batch.core)
    implementation(libs.spring.jdbc)
    runtimeOnly(libs.h2)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(project(":core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
    testRuntimeOnly(libs.h2)

    // Connectors needed for integration tests
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mongodb)
    testRuntimeOnly(project(":connectors:connector-postgresql"))
    testRuntimeOnly(project(":connectors:connector-mongodb"))
    testRuntimeOnly(libs.postgresql)
    testImplementation(libs.mongodb.driver.sync)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    enabled = false
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

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

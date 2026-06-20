plugins {
    `java-library`
}

description = "RecordRelay Elasticsearch connector — DataSourceConnector adapter"

dependencies {
    api(project(":recordrelay-core"))
    implementation(libs.elasticsearch.java)
    implementation(libs.jakarta.json.api)
    // Jackson as JSON-P provider (transitive from elasticsearch-java, explicit for clarity)
    implementation(libs.jackson.databind)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit5)
    testImplementation(libs.testcontainers.elasticsearch)
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

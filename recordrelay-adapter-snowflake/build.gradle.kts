plugins {
    `java-library`
}

description = "RecordRelay Snowflake connector — ContextProviderPort adapter for Snowflake data warehouses"

dependencies {
    api(project(":recordrelay-core"))
    implementation(libs.snowflake.jdbc)
    implementation(libs.hikaricp)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    enabled = false
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

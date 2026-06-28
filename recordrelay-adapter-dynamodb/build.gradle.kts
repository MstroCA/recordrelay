plugins {
    `java-library`
}

description = "RecordRelay DynamoDB connector — ContextProviderPort adapter for AWS DynamoDB"

dependencies {
    api(project(":recordrelay-core"))
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.dynamodb)
    implementation(libs.aws.auth)
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

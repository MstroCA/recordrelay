plugins {
    `java-library`
}

description = "RecordRelay Masking Engine — deterministic PII masking for EMAIL, PHONE, ADDRESS, IBAN, NATIONAL_ID"

dependencies {
    api(project(":recordrelay-domain"))
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
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("jacocoTestReport"))
    violationRules {
        rule {
            limit {
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

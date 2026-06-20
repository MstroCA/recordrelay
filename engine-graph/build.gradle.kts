plugins {
    `java-library`
}

description = "RecordRelay Graph Engine — FK-based and heuristic relationship discovery"

dependencies {
    api(project(":recordrelay-core"))
    api(project(":recordrelay-domain"))
    implementation(libs.hikaricp)
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
    enabled = false
}

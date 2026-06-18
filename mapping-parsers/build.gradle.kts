plugins {
    `java-library`
}

description = "RecordRelay mapping parsers — JSON, YAML, SQL, NoSQL formats with validation and transform functions"

dependencies {
    api(project(":core"))
    implementation(libs.jackson.databind)
    implementation(libs.snakeyaml)
    implementation(libs.jsqlparser)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(project(":core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    enabled = false
}

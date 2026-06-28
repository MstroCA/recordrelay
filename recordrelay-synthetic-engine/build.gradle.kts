plugins {
    `java-library`
}

description = "RecordRelay Synthetic Engine — schema-aware realistic fake data generation"

dependencies {
    api(project(":recordrelay-core"))
    implementation(libs.slf4j.api)
    compileOnly(libs.spotbugs.annotations)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}

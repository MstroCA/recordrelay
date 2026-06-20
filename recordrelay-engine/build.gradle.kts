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
}

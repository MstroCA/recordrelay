plugins {
    `java-library`
}

description = "RecordRelay engine-clone — Clone engine adapters: JDBC resolver, masking, .rrpkg I/O"

dependencies {
    api(project(":core"))
    api(project(":core-clone"))
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

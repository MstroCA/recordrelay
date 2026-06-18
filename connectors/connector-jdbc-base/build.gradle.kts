plugins {
    `java-library`
}

description = "RecordRelay JDBC base — shared abstract classes for SQL connector adapters"

dependencies {
    api(project(":core"))
    implementation(libs.hikaricp)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}

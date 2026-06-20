plugins {
    `java-library`
}

description = "RecordRelay SQLite connector — DataSourceConnector adapter"

dependencies {
    api(project(":recordrelay-adapter-jdbc-base"))
    implementation(libs.sqlite.jdbc)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}

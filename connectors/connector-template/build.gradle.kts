plugins {
    `java-library`
}

description = "RecordRelay connector template — starting point for custom connector implementations"

dependencies {
    api(project(":core"))
    compileOnly(libs.spotbugs.annotations)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

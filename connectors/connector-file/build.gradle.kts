plugins {
    `java-library`
}

description = "RecordRelay file connectors — CSV, Excel, JSON, YAML, Parquet adapters"

dependencies {
    api(project(":core"))
    implementation(libs.commons.csv)
    implementation(libs.poi.ooxml)
    implementation(libs.jackson.databind)
    implementation(libs.snakeyaml)
    implementation(libs.parquet.avro)
    implementation(libs.hadoop.common) {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
        exclude(group = "com.sun.jersey")
        exclude(group = "javax.servlet")
    }
    implementation(libs.hadoop.mapreduce.client.core) {
        exclude(group = "org.slf4j", module = "slf4j-log4j12")
        exclude(group = "log4j", module = "log4j")
        exclude(group = "com.sun.jersey")
        exclude(group = "javax.servlet")
    }
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

plugins {
    alias(libs.plugins.intellij.platform)
}

// dnsjava (transitive from cassandra-driver) registers a broken InetAddressResolverProvider
// that crashes JaCoCo's JVM agent startup. Exclude it project-wide in this module.
configurations.all {
    exclude(group = "dnsjava", module = "dnsjava")
}

description = "RecordRelay IntelliJ IDEA plugin"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.recordrelay.plugin"
        name = "RecordRelay"
        version = "0.1.0-SNAPSHOT"
        description =
            "Universal dynamic ETL — migrate data between SQL and NoSQL databases directly from IntelliJ IDEA."
        ideaVersion {
            sinceBuild = "241"
        }
    }
    // pluginVerification: run './gradlew :plugin:runPluginVerifier' manually to verify against
    // target IDEs. Requires network access; not wired into the standard check task.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }
    implementation(project(":core"))
    implementation(project(":cli"))
    implementation(libs.jackson.databind)
    runtimeOnly(project(":engine-batch"))
    runtimeOnly(project(":mapping-parsers"))
    runtimeOnly(project(":connectors:connector-postgresql"))
    runtimeOnly(project(":connectors:connector-mongodb"))
    runtimeOnly(project(":connectors:connector-mysql"))
    runtimeOnly(project(":connectors:connector-sqlserver"))
    runtimeOnly(project(":connectors:connector-oracle"))
    runtimeOnly(project(":connectors:connector-sqlite"))
    runtimeOnly(project(":connectors:connector-cassandra"))
    runtimeOnly(project(":connectors:connector-redis"))
    runtimeOnly(project(":connectors:connector-elasticsearch"))
    runtimeOnly(project(":connectors:connector-file"))
    compileOnly(libs.spotbugs.annotations)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testRuntimeOnly(libs.logback.classic)
}

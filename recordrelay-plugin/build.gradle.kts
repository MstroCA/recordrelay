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
        version = project.version.toString()
        description =
            "Universal Data Reproduction & Debug Platform — reproduce production context locally from IntelliJ IDEA."
        ideaVersion {
            sinceBuild = "241"
        }
    }
    // pluginVerification: run './gradlew :recordrelay-plugin:runPluginVerifier' manually.
    // Requires network access; not wired into the standard check task.
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
    implementation(project(":recordrelay-core"))
    implementation(project(":recordrelay-domain"))
    implementation(project(":recordrelay-cli"))
    implementation(project(":recordrelay-engine"))
    implementation(libs.jackson.databind)
    runtimeOnly(project(":recordrelay-graph-engine"))
    runtimeOnly(project(":recordrelay-masking-engine"))
    runtimeOnly(project(":recordrelay-package-engine"))
    runtimeOnly(project(":recordrelay-adapter-postgres"))
    runtimeOnly(project(":recordrelay-adapter-mongodb"))
    runtimeOnly(project(":recordrelay-adapter-mysql"))
    runtimeOnly(project(":recordrelay-adapter-sqlserver"))
    runtimeOnly(project(":recordrelay-adapter-oracle"))
    runtimeOnly(project(":recordrelay-adapter-sqlite"))
    runtimeOnly(project(":recordrelay-adapter-cassandra"))
    runtimeOnly(project(":recordrelay-adapter-redis"))
    runtimeOnly(project(":recordrelay-adapter-elasticsearch"))
    runtimeOnly(project(":recordrelay-adapter-file"))
    compileOnly(libs.spotbugs.annotations)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testRuntimeOnly(libs.logback.classic)
}

import com.diffplug.spotless.LineEnding

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.spotbugs) apply false
}

fun latestGitTag(): String =
    try {
        ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootDir)
            .start()
            .inputStream
            .bufferedReader()
            .readLine()
            ?.removePrefix("v")
            ?: "0.1.0-SNAPSHOT"
    } catch (_: Exception) {
        "0.1.0-SNAPSHOT"
    }

allprojects {
    group = "io.recordrelay"
    version = findProperty("releaseVersion")?.toString() ?: latestGitTag()
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    // Apply by string ID — the libs catalog accessor is not usable inside subprojects {}
    apply(plugin = "com.github.spotbugs")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // ── Checkstyle ──────────────────────────────────────────────────────────
    configure<CheckstyleExtension> {
        toolVersion = "10.18.1"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
        maxWarnings = 0
    }
    tasks.withType<Checkstyle> {
        configProperties!!["suppressionFile"] =
            rootProject.file("config/checkstyle/suppressions.xml").absolutePath
    }

    // ── SpotBugs ─────────────────────────────────────────────────────────────
    configure<com.github.spotbugs.snom.SpotBugsExtension> {
        toolVersion.set("4.8.6")
        ignoreFailures.set(false)
        effort.set(com.github.spotbugs.snom.Effort.MAX)
        reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    }
    tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
        reports.create("html") { enabled = true }
        reports.create("xml") { enabled = false }
    }

    // ── JaCoCo ───────────────────────────────────────────────────────────────
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }
    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
    // Coverage thresholds are configured per-module, not here, because connector modules
    // rely on integration tests for coverage and have a lower unit-test-only threshold.
}

// ── Spotless (root-level, applies to all source) ──────────────────────────────
spotless {
    lineEndings = LineEnding.UNIX
    java {
        target("**/*.java")
        googleJavaFormat("1.22.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("1.3.1")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

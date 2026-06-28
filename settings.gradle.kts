pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://plugins.jetbrains.com/maven")
    }
}

rootProject.name = "recordrelay"

// gradle/libs.versions.toml is auto-discovered as the default "libs" catalog

// ── Core ─────────────────────────────────────────────────────────────────────
include(
    "recordrelay-core",
    "recordrelay-domain",
)

// ── Engine ────────────────────────────────────────────────────────────────────
include(
    "recordrelay-graph-engine",
    "recordrelay-masking-engine",
    "recordrelay-package-engine",
    "recordrelay-synthetic-engine",
    "recordrelay-engine",
)

// ── Adapters ──────────────────────────────────────────────────────────────────
include(
    "recordrelay-adapter-jdbc-base",
    "recordrelay-adapter-postgres",
    "recordrelay-adapter-mysql",
    "recordrelay-adapter-mongodb",
    "recordrelay-adapter-redis",
    "recordrelay-adapter-sqlserver",
    "recordrelay-adapter-oracle",
    "recordrelay-adapter-sqlite",
    "recordrelay-adapter-cassandra",
    "recordrelay-adapter-elasticsearch",
    "recordrelay-adapter-file",
    "recordrelay-adapter-template",
    "recordrelay-adapter-snowflake",
    "recordrelay-adapter-dynamodb",
    "recordrelay-adapter-bigquery",
)

// ── Applications ──────────────────────────────────────────────────────────────
include(
    "recordrelay-cli",
    "recordrelay-desktop",
    "recordrelay-plugin",
)

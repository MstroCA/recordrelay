pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://plugins.jetbrains.com/maven")
    }
}

rootProject.name = "recordrelay"

// gradle/libs.versions.toml is auto-discovered as the default "libs" catalog

// ── Core ─────────────────────────────────────────────────────────────────────
include("recordrelay-core")
project(":recordrelay-core").projectDir = file("core")

include("recordrelay-domain")
project(":recordrelay-domain").projectDir = file("core-clone")

// ── Engine ────────────────────────────────────────────────────────────────────
include("recordrelay-graph-engine")
project(":recordrelay-graph-engine").projectDir = file("engine-graph")

include("recordrelay-masking-engine")
project(":recordrelay-masking-engine").projectDir = file("engine-masking")

include("recordrelay-package-engine")
project(":recordrelay-package-engine").projectDir = file("engine-package")

include("recordrelay-engine")
project(":recordrelay-engine").projectDir = file("engine-clone")

// ── Adapters ──────────────────────────────────────────────────────────────────
include("recordrelay-adapter-jdbc-base")
project(":recordrelay-adapter-jdbc-base").projectDir = file("connectors/connector-jdbc-base")

include("recordrelay-adapter-postgres")
project(":recordrelay-adapter-postgres").projectDir = file("connectors/connector-postgresql")

include("recordrelay-adapter-mysql")
project(":recordrelay-adapter-mysql").projectDir = file("connectors/connector-mysql")

include("recordrelay-adapter-mongodb")
project(":recordrelay-adapter-mongodb").projectDir = file("connectors/connector-mongodb")

include("recordrelay-adapter-redis")
project(":recordrelay-adapter-redis").projectDir = file("connectors/connector-redis")

include("recordrelay-adapter-sqlserver")
project(":recordrelay-adapter-sqlserver").projectDir = file("connectors/connector-sqlserver")

include("recordrelay-adapter-oracle")
project(":recordrelay-adapter-oracle").projectDir = file("connectors/connector-oracle")

include("recordrelay-adapter-sqlite")
project(":recordrelay-adapter-sqlite").projectDir = file("connectors/connector-sqlite")

include("recordrelay-adapter-cassandra")
project(":recordrelay-adapter-cassandra").projectDir = file("connectors/connector-cassandra")

include("recordrelay-adapter-elasticsearch")
project(":recordrelay-adapter-elasticsearch").projectDir = file("connectors/connector-elasticsearch")

include("recordrelay-adapter-file")
project(":recordrelay-adapter-file").projectDir = file("connectors/connector-file")

include("recordrelay-adapter-template")
project(":recordrelay-adapter-template").projectDir = file("connectors/connector-template")

// ── Applications ──────────────────────────────────────────────────────────────
include("recordrelay-cli")
project(":recordrelay-cli").projectDir = file("cli")

include("recordrelay-desktop")
project(":recordrelay-desktop").projectDir = file("desktop")

include("recordrelay-plugin")
project(":recordrelay-plugin").projectDir = file("plugin")

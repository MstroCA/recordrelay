pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://plugins.jetbrains.com/maven")
    }
}

rootProject.name = "recordrelay"

// gradle/libs.versions.toml is auto-discovered as the default "libs" catalog

include(
    "core",
    "core-clone",
    "connectors:connector-postgresql",
    "connectors:connector-mongodb",
    "connectors:connector-jdbc-base",
    "connectors:connector-mysql",
    "connectors:connector-sqlserver",
    "connectors:connector-oracle",
    "connectors:connector-sqlite",
    "connectors:connector-cassandra",
    "connectors:connector-redis",
    "connectors:connector-elasticsearch",
    "connectors:connector-file",
    "connectors:connector-template",
    "engine-clone",
    "cli",
    "desktop",
    "plugin",
)

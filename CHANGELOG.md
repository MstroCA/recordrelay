# Changelog

All notable changes to RecordRelay are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- DB-driven Clone Context UI in both Desktop and IntelliJ Plugin — root table loaded via `SchemaInspector.listTables()`, no hardcoded entity picker
- Export mode in plugin `CloneContextPanel` — `JFileChooser` output directory picker, calls `ContextClonePlan.bugCapture()`
- Clone history dashboard in plugin `MonitorPanel` — KPI metrics (total clones, success rate, avg duration), history table, health check
- Graph view for relationship visualisation in Desktop
- Field overrides in `ContextClonePlan.liveCloneWithOverrides()` — per-column value override map
- `DiscoveryPanel` in plugin — column inspection (Column/Type/Nullable/PK) and cross-DB compatibility map
- `ConnectorRegistry.reloadFrom(ClassLoader)` — allows IntelliJ plugin to reload the ServiceLoader with the plugin classloader
- TCCL swap in `DataSourceFactory` and `JdbcDataSourceFactory` around `HikariDataSource` construction — fixes JDBC driver loading in IntelliJ plugin sandbox
- `onImportCompleted` callback in `CloneProgressListener` — CLI now prints import record count per table
- `Multi-Release: true` manifest attribute in CLI shadow JAR — fixes dnsjava `InetAddressResolverProvider` crash on Java 21+
- `.gitattributes` enforcing LF line endings on Windows runners — fixes Spotless CRLF failures in CI
- 28-table SaaS + e-commerce test schema for postgres1 (with seed data) and postgres2 (schema only)

### Changed
- All Gradle modules renamed to `recordrelay-*` convention (e.g. `recordrelay-adapter-postgres`, `recordrelay-engine`)
- `DataSourceConnector` SPI renamed to `ContextProviderPort` — aligns with data reproduction platform pivot
- CLI command set pivoted: `migrate`/`inspect` replaced by `clone`, `export`, `import`, `replay`, `diff`, `discover`, `analyze`, `conn`, `env`, `status`
- Desktop Clone Context screen rewritten — DB-driven table discovery replaces abstract entity picker
- All documentation translated to English and updated to reflect current module names, commands, and architecture

### Fixed
- Static initializer ordering crash in `BuiltinEntityRegistry`
- Typed PK binding for non-integer PKs in record fetcher
- Discovery column display (Column/Type/Nullable/PK) in Desktop
- CI module path references after directory rename
- Output-dir row missing in Desktop export mode
- Unknown fields in `CliConfig` causing failures on legacy config files
- Apple Silicon (arm64) JDK for Desktop `runTask`

## [0.1.0] — 2026-06-01

### Added
- Phase 1–6: Core domain model (`ConnectionProfile`, `DataRecord`, `MappingDefinition`, etc.)
- Hexagonal architecture — `DataSourceConnector` SPI via ServiceLoader
- PostgreSQL connector (HikariCP, streaming cursor reader, batch writer)
- MongoDB connector (Atlas-compatible, cursor-based streaming)
- Batch engine using Spring Batch (`BatchJobService`, `RecordRelayJob`)
- Mapping DSL parser — SQL dialect and YAML dialect
- CLI (`rr-cli`) using Picocli with `migrate`, `inspect`, `list-databases`, `list-tables` commands
- JavaFX desktop application
- IntelliJ IDEA plugin
- Phase 7: CI/CD workflows (GitHub Actions), Dependabot, security scanning (CodeQL + OWASP)
- JaCoCo coverage reporting via Codecov
- Spotless, Checkstyle, SpotBugs static analysis in CI

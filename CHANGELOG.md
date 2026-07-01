# Changelog

All notable changes to RecordRelay are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Per-connection **schema** support — connections take an optional schema (PostgreSQL `currentSchema`/`search_path`) so unqualified table names resolve in that schema across every operation (fetch, write, identity allocation, sequence sync). Set via `rr conn add --schema <name>` or the Schema field in the Desktop/Plugin connection dialogs. Fixes cross-schema satellites (e.g. `read_model` in `beyan_kullanici_yonetimi`)
- Two new identity-allocation strategies in the conflict/identity selector: **`SEQUENCE`** (allocate IDs straight from the target table's native identity/serial sequence via `nextval`, i.e. exactly what the DB would assign — no post-write sequence drift; PostgreSQL, falls back to `max(id)+1` elsewhere) and **`START_AT`** (allocate sequentially from a caller-supplied start value, floor-protected against existing rows). Exposed as `--id-start` on the CLI and a "Start ID" field in Desktop and Plugin. New `identityStart` on `CloneRequest`/`ContextClonePlan`
- Satellite (companion) tables — after a clone, sync rows from a table in a **separate** database that references the cloned root by a single link column (e.g. an event-sourced `read_model` in a user-module DB normally populated by Kafka). The engine copies the matching rows, remaps the link column to the newly allocated root id, regenerates the companion PK, applies the same field overrides, and bumps the target sequence. Defined once per entity in `config.json` (`satellites` map) and honoured by CLI, Desktop, and Plugin; CLI also supports ad-hoc `--satellite sourceConn>targetConn:table.linkColumn[.pkColumn]`. New: `SatelliteTable`/`SatelliteConfig` domain types, `SatelliteSyncPort` + `JdbcSatelliteSyncer`, `SatelliteConfigResolver`
- Satellite editors in the Desktop (editable table) and IntelliJ Plugin (spec textarea with Load/Save-to-config) Clone Context screens
- End-to-end Testcontainers integration test (`SatelliteCloneIT`, two-DB topology) proving link remap + field overrides across databases; run with `./gradlew :recordrelay-engine:integrationTest`
- Self-hosted plugin update channel — `generateUpdatePluginsXml` Gradle task emits a custom-repository `updatePlugins.xml` so IDEA detects new versions without uninstall/reinstall (see `docs/plugin-updates.md`)
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

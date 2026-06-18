# Changelog

All notable changes to RecordRelay are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/);
versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Phase 8: Additional SQL connectors — MySQL/MariaDB, SQL Server, Oracle, SQLite
- Phase 8: NoSQL connectors — Cassandra (DataStax driver), Redis (Lettuce), Elasticsearch (Java API client)
- Phase 8: File connectors — CSV (Commons CSV), Excel (POI SXSSF), JSON Lines (Jackson), YAML (SnakeYAML), Parquet (parquet-avro)
- `connector-template` module as a starting point for custom connectors
- `connector-jdbc-base` with abstract base classes for JDBC connectors (HikariCP pooling, batch read/write)
- `HealthStatus` record with `healthCheck()` default on `DataSourceConnector`
- `FILE_CSV`, `FILE_EXCEL`, `FILE_JSON`, `FILE_YAML`, `FILE_PARQUET` entries in `DatabaseType`
- Testcontainers integration tests for all new connectors

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

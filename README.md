# RecordRelay

**Universal Data Reproduction & Debug Platform** — Reproduce real production or test system state locally in minutes.

[![CI](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml/badge.svg)](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/MstroCA/recordrelay?label=release)](https://github.com/MstroCA/recordrelay/releases/latest)
[![Coverage](https://codecov.io/gh/MstroCA/recordrelay/graph/badge.svg)](https://codecov.io/gh/MstroCA/recordrelay)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/io.recordrelay.plugin?label=marketplace)](https://plugins.jetbrains.com/plugin/io.recordrelay.plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

---

## Documentation

| Language | Guide |
|----------|-------|
| English | [User Guide](docs/user-guide.md) |
| Türkçe | [Kullanıcı Kılavuzu](docs/user-guide.tr.md) |

| Reference | Link |
|-----------|------|
| CLI Commands | [CLI Reference](docs/cli-reference.md) |
| Architecture | [Architecture](docs/architecture.md) |
| Batch Tuning | [Batch Tuning](docs/batch-tuning.md) |
| Masking DSL | [Mapping DSL](docs/mapping-dsl.md) |
| Plugin Development | [Plugin Development](docs/plugin-development.md) |

---

## Vision

RecordRelay is not an ETL tool. It is a **business context reproduction** platform for developers and SREs: it reproduces a real entity — a customer, order, or user — along with all its relationships, from production to a local environment in minutes, or packages it as a `.rrpkg` file to share with others.

**Beyond a single database:**
- **Satellite (companion) tables** — reach into a *separate* database whose rows reference the cloned root by a single logical column (e.g. an event-sourced `read_model` normally produced by Kafka). After the clone, the matching rows are copied with the link column remapped to the newly allocated root id and the same field overrides applied — no message bus required.
- **Identity strategies** — `REGENERATE_IDENTITIES` (default), `ISOLATE_NAMESPACE`, `SKIP_EXISTING`, `FAIL_SAFE`, `SEQUENCE` (allocate from the target's native identity sequence) and `START_AT` (custom start value).
- **Per-connection schema** and **schema-drift tolerance** — connections carry an optional schema (PostgreSQL `search_path`); writes insert only the columns that exist in the target, skipping source-only columns instead of failing.

**Three deployment targets:**
- **Desktop** — JavaFX desktop application (AtlantaFX) — 14 screens covering cloning, discovery, query analysis, ERD graph view, schema drift, masking coverage, presets, scheduled sync, and in-app help
- **CLI** — command-line tool that integrates into CI/CD pipelines
- **IntelliJ Plugin** — Clone, Connections, Discovery, Monitor, and Query tabs directly inside the IDE

---

## Desktop Screens

| Screen | Description |
|--------|-------------|
| **Clone Context** | Select source / target connection, enter entity + ID, configure depth and masking, run with live phase log |
| **Environments** | Manage named environment groups (prod, staging, local) and assign connections to them |
| **Connections** | Add, edit, and test connection profiles for any supported source |
| **Discovery** | Browse databases, schemas, and table structures across any connected source |
| **Monitor** | Real-time clone-job monitor with sliding throughput chart and structured phase log |
| **Graph View** | ERD-style relationship graph — Bezier FK edges, hover highlights, zoom slider, Ctrl+scroll zoom, fit-to-screen |
| **Migration Drift** | Side-by-side schema diff between two environments — detects added/removed/changed columns and indexes |
| **Row Count Diff** | Compare row counts per table across two environments to spot data divergence |
| **Query Analyzer** | Visual flow query builder (drag tables → connect ports → get SQL) or raw SQL editor with live results |
| **Connection Health** | Ping all configured connections and report latency and reachability |
| **Masking Coverage** | Visual report of which columns have masking rules applied across all tables |
| **Clone Presets** | Save and reuse clone configurations (entity, depth, conflict resolution, masking profile) |
| **Scheduled Sync** | Define recurring clone jobs with cron-style schedules and target environment |
| **Help** | In-app searchable guide covering all 16 topics in the active UI language (EN/TR/DE/FR/ES/IT/PT/RU/ZH/JA/KO/AR) |

> **Conflict Resolution** — when cloning into a target that already has data, the *On conflict* combobox controls behavior: **Regenerate Identities** (allocates new IDs above both source and target max — safe for non-empty targets), **Isolate Namespace**, **Fail Safe**, or **Skip Existing**. Tables are always written in FK-safe topological order. See the [User Guide](docs/user-guide.md#conflict-resolution) for details.

---

## Query Analyzer — Visual Flow Builder

The Query Analyzer offers two modes toggled by a toolbar button:

**Flow mode** (default)
1. Select a connection and database from the toolbar
2. Double-click any table in the left panel to add it as a node card on the canvas
3. Click the port button (○) on a column to start a JOIN — click a port on another table to finish it; a dialog asks for `INNER`, `LEFT`, or `RIGHT`
4. Check/uncheck column checkboxes to control the `SELECT` list
5. Add `WHERE` filters, `ORDER BY` clauses, and a row `LIMIT` in the right panel
6. The **Generated SQL** preview updates live — hit **Run** to execute

**SQL mode**
Raw text editor for hand-written queries, same Run button and results table.

Both the **IntelliJ plugin** (Swing canvas) and the **desktop app** (JavaFX canvas) share the same `QueryFlowModel` in `recordrelay-cli`, keeping SQL generation logic in one place.

---

## Graph View

The Graph View renders an ERD-style relationship map rooted at any table:

- **Bezier FK edges** — cubic curves with directional control points distinguish FK from heuristic links
- **Hover highlight** — mousing over a node dims unrelated edges so you can trace one relationship at a time
- **Zoom** — slider, `+` / `−` buttons, Ctrl+scroll, and a **Fit to Screen** button
- **Root table selection** — choose any table as the root; the graph redraws from that perspective

---

## CLI Usage

```bash
# Add a connection
rr conn add --name prod --type POSTGRESQL --host db.prod --port 5432 --database mydb --user admin
rr conn add --name local --type POSTGRESQL --host localhost --port 5432 --database mydb --user admin

# Clone a customer from production to local
rr clone --entity customer --id 12345 --from prod --target local --depth 3

# Verbose logging (SQL + per-table/per-record detail)
rr clone --entity customer --id 12345 --from prod --target local -v

# Identity strategy: allocate IDs from the target's native sequence, or a custom start
rr clone --entity order --id 987 --from prod --target local --conflict SEQUENCE
rr clone --entity order --id 987 --from prod --target local --conflict START_AT --id-start 100000

# Connection with a non-default schema (PostgreSQL search_path)
rr conn add --name prod-user --type POSTGRESQL --host db.prod --port 5432 \
    --database userdb --user admin --schema app_schema

# Satellite (companion) table in a SEPARATE database, linked by a single column.
# After the clone, matching rows are copied with the link column remapped to the new
# root id and the same field overrides applied. Also definable per-entity in config.json.
rr clone --entity beyanname --id 12 --from prod-core --target test-core \
    --override mukellef_vkn=1234567890 \
    --satellite "prod-user>test-user:read_model.beyanname_id"

# Export to a package (share with others)
rr export --entity customer --id 12345 --from prod --output ./exports/

# Import a package into another environment
rr import customer-12345-1234567890.rrpkg --target staging

# Compare two environments
rr diff --entity customer --id 12345 --from prod --to staging

# Schema discovery
rr discover --source prod

# Start headless REST API server (for CI/CD pipelines)
rr serve --port 8080
# GET  /health  GET /presets  GET /syncs
# POST /clone   POST /preset/run/{name}   POST /sync/run/{name}
```

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                         UI LAYER                             │
│        Desktop (JavaFX) │ CLI (Picocli) │ IntelliJ Plugin    │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│                  APPLICATION / ENGINE                        │
│   recordrelay-engine (orchestration + identity mapping)      │
│   recordrelay-graph-engine  (FK & heuristic discovery)       │
│   recordrelay-masking-engine  (deterministic PII masking)    │
│   recordrelay-package-engine  (.rrpkg v2.1 export/import)    │
└────────────────────────┬─────────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────────┐
│                  DOMAIN & CORE                               │
│   recordrelay-domain  (BusinessEntity, RelationshipGraph,    │
│     ContextClonePlan, IdentityMapping, MaskingConfig…)       │
│   recordrelay-core  (ConnectionProfile, ContextProviderPort  │
│     SPI, RecordReader/Writer, ConnectorRegistry)             │
└────────────────────────┬─────────────────────────────────────┘
                         │ implements ContextProviderPort (ServiceLoader)
┌────────────────────────▼─────────────────────────────────────┐
│                  ADAPTERS (10 connectors)                    │
│  SQL: postgres │ mysql │ sqlserver │ oracle │ sqlite         │
│  NoSQL: mongodb │ cassandra │ redis │ elasticsearch          │
│  File: CSV │ Excel │ JSON │ YAML │ Parquet                   │
└──────────────────────────────────────────────────────────────┘
```

Hexagonal Architecture (Ports & Adapters): the domain and core modules contain only interface definitions. They have no dependency on any DB driver or UI framework. Connectors and engines are adapters.

---

## Modules

| Module | Description |
|--------|-------------|
| `recordrelay-core` | ConnectionProfile, ContextProviderPort SPI, ConnectorRegistry |
| `recordrelay-domain` | BusinessEntity, RelationshipGraph, ContextClonePlan, IdentityMapping, MaskingConfig |
| `recordrelay-engine` | Clone orchestration, identity mapping, BFS extraction, replay |
| `recordrelay-graph-engine` | FK-based and heuristic relationship discovery |
| `recordrelay-masking-engine` | Deterministic PII masking (EMAIL, PHONE, ADDRESS, IBAN, NATIONAL_ID) |
| `recordrelay-package-engine` | `.rrpkg` v2.1 ZIP export and import |
| `recordrelay-cli` | Picocli CLI commands + shared `QueryFlowModel` (SQL generation for the flow query builder) + `rr serve` REST API server + `WebhookNotifier` |
| `recordrelay-desktop` | JavaFX app — 14 screens: Clone Context, Environments, Connections, Discovery, Monitor, Graph View, Migration Drift, Row Count Diff, Query Analyzer, Connection Health, Masking Coverage, Clone Presets, Scheduled Sync, Help |
| `recordrelay-plugin` | IntelliJ IDEA plugin — Clone, Connections, Discovery, Monitor, Query tabs |
| `recordrelay-adapter-jdbc-base` | Abstract base for JDBC connectors |
| `recordrelay-adapter-postgres` | PostgreSQL 14+ |
| `recordrelay-adapter-mysql` | MySQL 8+ / MariaDB 10.6+ |
| `recordrelay-adapter-sqlserver` | Microsoft SQL Server 2019+ |
| `recordrelay-adapter-oracle` | Oracle 19c+ |
| `recordrelay-adapter-sqlite` | SQLite (embedded) |
| `recordrelay-adapter-mongodb` | MongoDB 6+ |
| `recordrelay-adapter-cassandra` | Apache Cassandra 4+ |
| `recordrelay-adapter-redis` | Redis 7+ (Lettuce) |
| `recordrelay-adapter-elasticsearch` | Elasticsearch 8+ |
| `recordrelay-adapter-file` | CSV, Excel, JSON Lines, YAML, Parquet |
| `recordrelay-adapter-template` | Template for building custom connectors |

---

## Supported Sources

### SQL Databases
| Database | Version | Reader | Writer | Schema |
|----------|---------|:------:|:------:|:------:|
| PostgreSQL | 14+ | ✓ | ✓ | ✓ |
| MySQL / MariaDB | 8+ / 10.6+ | ✓ | ✓ | ✓ |
| SQL Server | 2019+ | ✓ | ✓ | ✓ |
| Oracle | 19c+ | ✓ | ✓ | ✓ |
| SQLite | 3.x | ✓ | ✓ | ✓ |

### NoSQL Databases
| Database | Version | Reader | Writer | Schema |
|----------|---------|:------:|:------:|:------:|
| MongoDB | 6+ | ✓ | ✓ | ✓ |
| Cassandra | 4+ | ✓ | ✓ | ✓ |
| Redis | 7+ | ✓ | ✓ | — |
| Elasticsearch | 8+ | ✓ | ✓ | ✓ |

### File Formats
| Format | Reader | Writer |
|--------|:------:|:------:|
| CSV | ✓ | ✓ |
| Excel (.xlsx) | ✓ | ✓ |
| JSON Lines | ✓ | ✓ |
| YAML | ✓ | ✓ |
| Parquet | ✓ | ✓ |

---

## Installation

### Desktop (macOS / Windows / Linux)

Download the package for your platform from the [GitHub Releases](https://github.com/MstroCA/recordrelay/releases/latest) page.

| Platform | Package |
|----------|---------|
| macOS | `.dmg` |
| Windows | `.exe` installer (Start menu shortcut included) |
| Linux | `.deb` |

### CLI

```bash
curl -L https://github.com/MstroCA/recordrelay/releases/latest/download/rr-cli-x.y.z.tar.gz | tar xz
export PATH="$PWD/rr-cli-x.y.z/bin:$PATH"
rr --version
```

> Requirement: Java 21+

### IntelliJ IDEA Plugin

**Settings → Plugins → Marketplace → "RecordRelay"**

---

## Building from Source

```bash
git clone https://github.com/MstroCA/recordrelay.git
cd recordrelay

# Compile + static analysis + unit tests
./gradlew build

# Integration tests (requires Docker)
./gradlew integrationTest

# Build CLI fat JAR
./gradlew :recordrelay-cli:shadowJar
# → recordrelay-cli/build/libs/rr-cli-<version>.jar

# Desktop (run locally)
./gradlew :recordrelay-desktop:run

# Desktop distribution
./gradlew :recordrelay-desktop:installDist

# Plugin ZIP
./gradlew :recordrelay-plugin:buildPlugin
```

---

## Writing a New Connector

Copy the `recordrelay-adapter-template` module and implement the `ContextProviderPort` interface:

```java
public class MyConnector implements ContextProviderPort {
    @Override public String connectorId() { return "my-db"; }
    @Override public boolean supports(ConnectionProfile p) { ... }
    // testConnection, listDatabases, schemaInspector, createReader, createWriter, healthCheck
}
```

Add the class name to `META-INF/services/io.recordrelay.core.port.out.ContextProviderPort` for SPI registration. Once the JAR is on the classpath, `ServiceLoader` discovers it automatically.

---

## Localization

RecordRelay ships with UI translations for 12 languages (EN, TR, DE, FR, ES, IT, PT, RU, ZH, JA, KO, AR).  
User-facing documentation is available in [English](docs/user-guide.md) and [Turkish](docs/user-guide.tr.md).  
To contribute a translation, add a `messages_<lang>.properties` file under `recordrelay-core/src/main/resources/io/recordrelay/core/i18n/` following the existing format.

---

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md). All contributions are accepted under the Apache-2.0 license.

---

## License

[Apache License 2.0](LICENSE) — Copyright 2026 the RecordRelay authors

# RecordRelay

**Universal Data Reproduction & Debug Platform** — Reproduce real production or test system state locally in minutes.

[![CI](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml/badge.svg)](https://github.com/MstroCA/recordrelay/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/MstroCA/recordrelay?label=release)](https://github.com/MstroCA/recordrelay/releases/latest)
[![Coverage](https://codecov.io/gh/MstroCA/recordrelay/graph/badge.svg)](https://codecov.io/gh/MstroCA/recordrelay)
[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/io.recordrelay.plugin?label=marketplace)](https://plugins.jetbrains.com/plugin/io.recordrelay.plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)

---

## Vision

RecordRelay is not an ETL tool. It is a **business context reproduction** platform for developers and SREs: it reproduces a real entity — a customer, order, or user — along with all its relationships, from production to a local environment in minutes, or packages it as a `.rrpkg` file to share with others.

**Three deployment targets:**
- **Desktop** — JavaFX desktop application (AtlantaFX) — one-click reproduction via the Clone Context screen
- **CLI** — command-line tool that integrates into CI/CD pipelines
- **IntelliJ Plugin** — Clone, Connections, Discovery, and Monitor tabs directly inside the IDE

---

## CLI Usage

```bash
# Add a connection
rr conn add --name prod --type POSTGRESQL --host db.prod --port 5432 --database mydb --user admin
rr conn add --name local --type POSTGRESQL --host localhost --port 5432 --database mydb --user admin

# Clone a customer from production to local
rr clone --entity customer --id 12345 --from prod --target local --depth 3

# Export to a package (share with others)
rr export --entity customer --id 12345 --from prod --output ./exports/

# Import a package into another environment
rr import customer-12345-1234567890.rrpkg --target staging

# Compare two environments
rr diff --entity customer --id 12345 --from prod --to staging

# Schema discovery
rr discover --source prod
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
│                  ADAPTERS (11 connectors)                    │
│  SQL: postgres │ mysql │ sqlserver │ oracle │ sqlite         │
│  NoSQL: mongodb │ cassandra │ redis │ elasticsearch          │
│  File: CSV │ Excel │ JSON │ YAML │ Parquet                   │
└──────────────────────────────────────────────────────────────┘
```

Hexagonal Architecture (Ports & Adapters): the domain and core modules contain only interface definitions. They have no dependency on any DB driver or UI framework. Connectors and engines are adapters.

---

## Modules

| Module | Directory | Description |
|--------|-----------|-------------|
| `recordrelay-core` | `recordrelay-core/` | ConnectionProfile, ContextProviderPort SPI, ConnectorRegistry |
| `recordrelay-domain` | `recordrelay-domain/` | BusinessEntity, RelationshipGraph, ContextClonePlan, IdentityMapping, MaskingConfig |
| `recordrelay-engine` | `recordrelay-engine/` | Clone orchestration, identity mapping, BFS extraction, replay |
| `recordrelay-graph-engine` | `recordrelay-graph-engine/` | FK-based and heuristic relationship discovery |
| `recordrelay-masking-engine` | `recordrelay-masking-engine/` | Deterministic PII masking (EMAIL, PHONE, ADDRESS, IBAN, NATIONAL_ID) |
| `recordrelay-package-engine` | `recordrelay-package-engine/` | `.rrpkg` v2.1 ZIP export and import |
| `recordrelay-cli` | `recordrelay-cli/` | Picocli CLI — clone, export, import, replay, diff, discover, analyze, env, conn, status |
| `recordrelay-desktop` | `recordrelay-desktop/` | JavaFX desktop application — Clone Context, Environments, Connections, Discovery, Monitor |
| `recordrelay-plugin` | `recordrelay-plugin/` | IntelliJ IDEA plugin — Clone, Connections, Discovery, Monitor tabs |
| `recordrelay-adapter-jdbc-base` | `recordrelay-adapter-jdbc-base/` | Abstract base for JDBC connectors |
| `recordrelay-adapter-postgres` | `recordrelay-adapter-postgres/` | PostgreSQL 14+ |
| `recordrelay-adapter-mysql` | `recordrelay-adapter-mysql/` | MySQL 8+ / MariaDB 10.6+ |
| `recordrelay-adapter-sqlserver` | `recordrelay-adapter-sqlserver/` | Microsoft SQL Server 2019+ |
| `recordrelay-adapter-oracle` | `recordrelay-adapter-oracle/` | Oracle 19c+ |
| `recordrelay-adapter-sqlite` | `recordrelay-adapter-sqlite/` | SQLite (embedded) |
| `recordrelay-adapter-mongodb` | `recordrelay-adapter-mongodb/` | MongoDB 6+ |
| `recordrelay-adapter-cassandra` | `recordrelay-adapter-cassandra/` | Apache Cassandra 4+ |
| `recordrelay-adapter-redis` | `recordrelay-adapter-redis/` | Redis 7+ (Lettuce) |
| `recordrelay-adapter-elasticsearch` | `recordrelay-adapter-elasticsearch/` | Elasticsearch 8+ |
| `recordrelay-adapter-file` | `recordrelay-adapter-file/` | CSV, Excel, JSON Lines, YAML, Parquet |
| `recordrelay-adapter-template` | `recordrelay-adapter-template/` | Template for building custom connectors |

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
# → recordrelay-cli/build/libs/rr-cli-0.1.0-SNAPSHOT.jar

# Desktop
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

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md). All contributions are accepted under the Apache-2.0 license.

---

## License

[Apache License 2.0](LICENSE) — Copyright 2026 the RecordRelay authors

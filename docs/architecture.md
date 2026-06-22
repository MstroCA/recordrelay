# Architecture

RecordRelay follows a **hexagonal (ports & adapters)** architecture. The core domain is
isolated from infrastructure; connectors are discovered at runtime via Java's `ServiceLoader`.

## Module overview

```
recordrelay-core/          ConnectionProfile, ContextProviderPort SPI, ConnectorRegistry
recordrelay-domain/        Immutable domain objects — BusinessEntity, RelationshipGraph,
                             ContextClonePlan, IdentityMapping, MaskingConfig

recordrelay-engine/        Clone orchestration — DefaultContextCloneEngine, DefaultCloneEngine,
                             DefaultIdentityMapper, JdbcRecordFetcher, FkRemapper,
                             DefaultReplayEngine, JdbcSequenceSynchronizer, CloneHistoryStore
recordrelay-graph-engine/  Relationship discovery — JdbcRelationshipResolver (FK-based),
                             HeuristicRelationshipResolver (naming convention), DefaultContextResolver
recordrelay-masking-engine/ Deterministic PII masking — DefaultMaskingService
                             (EMAIL, PHONE, ADDRESS, IBAN, NATIONAL_ID)
recordrelay-package-engine/ .rrpkg v2.1 export/import — RrPkgExporter, RrPkgImporter

recordrelay-cli/           Picocli fat-JAR (rr-cli) — clone, export, import, replay, diff,
                             discover, analyze, conn, env, status
recordrelay-desktop/       JavaFX desktop application (AtlantaFX) — Clone Context, Environments,
                             Connections, Discovery, Monitor
recordrelay-plugin/        IntelliJ IDEA plugin — Clone, Connections, Discovery, Monitor tabs

recordrelay-adapter-jdbc-base/    Abstract JDBC base (HikariCP pooling, batch read/write)
recordrelay-adapter-postgres/     PostgreSQL 14+
recordrelay-adapter-mysql/        MySQL 8+ / MariaDB 10.6+
recordrelay-adapter-sqlserver/    SQL Server 2019+
recordrelay-adapter-oracle/       Oracle 19c+
recordrelay-adapter-sqlite/       SQLite 3.x (embedded)
recordrelay-adapter-mongodb/      MongoDB 6+
recordrelay-adapter-cassandra/    Apache Cassandra 4+ (DataStax driver)
recordrelay-adapter-redis/        Redis 7+ (Lettuce)
recordrelay-adapter-elasticsearch/ Elasticsearch 8+ (Java API client)
recordrelay-adapter-file/         CSV, Excel (POI), JSON Lines, YAML, Parquet
recordrelay-adapter-template/     Starting point for custom connectors
```

## Request flow

```
CLI / Desktop / Plugin
        │
        ▼
ContextClonePlan.liveCloneWithOverrides()   ← builds the plan
        │
        ▼
DefaultContextCloneEngine.execute(plan, listener)
        │                        │
        ▼                        ▼
JdbcRelationshipResolver    DefaultMaskingService
(FK + heuristic graph)      (deterministic PII mask)
        │
        ├── JdbcRecordFetcher   (BFS extraction per table)
        ├── DefaultIdentityMapper (PK remapping across envs)
        ├── FkRemapper          (fix FK references in cloned rows)
        └── ConnectorRegistry.findConnector(profile)
              │
              ├── ContextProviderPort.createReader()  (streams rows)
              └── ContextProviderPort.createWriter()  (batch insert)
```

For export-only flows (`bugCapture`), `RrPkgExporter` replaces the writer and produces a
`.rrpkg` ZIP file. `RrPkgImporter` + `DefaultReplayEngine` replays the package into a target.

## Connector SPI

Every connector implements `ContextProviderPort` (in `recordrelay-core`):

```java
public interface ContextProviderPort {
  String connectorId();
  boolean supports(ConnectionProfile profile);
  void testConnection(ConnectionProfile profile) throws ConnectorException;
  List<DatabaseRef> listDatabases(ConnectionProfile profile) throws ConnectorException;
  SchemaInspector schemaInspector();
  RecordReader createReader();
  RecordWriter createWriter();

  // Default: wraps testConnection() with timing
  default HealthStatus healthCheck(ConnectionProfile profile) { ... }
}
```

Connectors register via `META-INF/services/io.recordrelay.core.port.out.ContextProviderPort`.
`ConnectorRegistry.findConnector(profile)` iterates `ServiceLoader` and picks the first
connector for which `supports(profile)` returns `true`.

In an IntelliJ plugin, call `ConnectorRegistry.reloadFrom(MyService.class.getClassLoader())`
at startup so the plugin classloader's adapters are visible to the registry.

## Security model

Passwords are stored as `ENC(AES256:<base64>)`. They are **never** logged; only
`profile.name()` appears in log messages. See [SECURITY.md](../SECURITY.md).

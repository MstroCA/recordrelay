# Architecture

RecordRelay follows a **hexagonal (ports & adapters)** architecture. The core domain is
isolated from infrastructure; connectors are discovered at runtime via Java's `ServiceLoader`.

## Module overview

```
core/                      Pure domain model + ports
  domain/                  Immutable value objects (record types)
  port/in/                 Use-case interfaces (TransferUseCase, etc.)
  port/out/                Adapter interfaces (DataSourceConnector, RecordReader, ...)
  spi/                     ConnectorRegistry — ServiceLoader wrapper

connectors/
  connector-postgresql/    PostgreSQL adapter
  connector-mongodb/       MongoDB adapter
  connector-jdbc-base/     Abstract JDBC base (AbstractJdbcConnector, etc.)
  connector-mysql/         MySQL/MariaDB adapter (extends jdbc-base)
  connector-sqlserver/     SQL Server adapter (standalone — semicolon URL format)
  connector-oracle/        Oracle adapter (standalone — thin URL format)
  connector-sqlite/        SQLite adapter
  connector-cassandra/     Cassandra adapter (DataStax driver)
  connector-redis/         Redis adapter (Lettuce)
  connector-elasticsearch/ Elasticsearch adapter (Java API client 8.x)
  connector-file/          File connectors: CSV, Excel, JSON, YAML, Parquet
  connector-template/      Starting point for custom connectors

engine-batch/              Spring Batch job — RecordRelayJob
mapping-parsers/           SQL and YAML mapping DSL parsers

cli/                       Picocli CLI — rr-cli fat-JAR (Shadow)
desktop/                   JavaFX desktop application
plugin/                    IntelliJ IDEA plugin
```

## Request flow

```
CLI / Desktop / Plugin
        │
        ▼
TransferUseCase (core/port/in)
        │
        ▼
BatchJobService → RecordRelayJob (Spring Batch)
        │                    │
        ▼                    ▼
ConnectorRegistry     MappingParser
  ServiceLoader     (SQL or YAML DSL)
        │
        ├─── DataSourceConnector.supports(profile) → selected connector
        │                    │
        ├── RecordReader ◄───┘   (streams rows / documents / lines)
        └── RecordWriter ◄───┘   (batch insert / bulk index / HSET)
```

## Connector SPI

Every connector implements `DataSourceConnector` (in `core/port/out`):

```java
public interface DataSourceConnector {
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

Connectors register via `META-INF/services/io.recordrelay.core.port.out.DataSourceConnector`.
`ConnectorRegistry.findConnector(profile)` iterates `ServiceLoader` and picks the first
connector for which `supports(profile)` returns `true`.

## Security model

Passwords are stored as `ENC(AES256:<base64>)`. They are **never** logged; only
`profile.name()` appears in log messages. See [SECURITY.md](../SECURITY.md).

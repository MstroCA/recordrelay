# Performance Tuning Guide

RecordRelay's engine uses BFS-based context extraction with per-table batch reads and writes.
This guide explains the available knobs for tuning throughput and memory usage.

## How the engine works

`DefaultContextCloneEngine` drives the clone:

1. **Graph resolution** — `JdbcRelationshipResolver` (FK metadata) + `HeuristicRelationshipResolver`
   (naming conventions) build a `RelationshipGraph` for the root entity.
2. **BFS extraction** — `JdbcRecordFetcher` walks the graph level by level, reading rows via
   `ContextProviderPort.createReader()` in configurable batches.
3. **Identity mapping** — `DefaultIdentityMapper` allocates new PKs for the target environment.
4. **FK remapping** — `FkRemapper` rewrites FK references in the cloned row set.
5. **Write** — `ContextProviderPort.createWriter()` inserts rows in batches, then
   `JdbcSequenceSynchronizer` advances sequences / auto-increment counters.

## Depth

```bash
rr clone --from prod --target local --customer-id 42 --depth 5
```

`--depth` (default: 3, max: 10) controls how many levels of FK relationships are followed.
Higher depth = more tables = more data = slower. For most debugging scenarios 3–4 is sufficient.

## JDBC fetch size

The JDBC reader uses a **server-side cursor** (`TYPE_FORWARD_ONLY`, `CONCUR_READ_ONLY`) with
`Statement.setFetchSize`. This streams rows without loading the full result set into memory.

The default fetch size is **1 000 rows per round-trip**. Override it per connection via the
`properties` map in your connection profile:

```yaml
# ~/.recordrelay/config.yaml
connections:
  prod:
    properties:
      fetchSize: "5000"
```

For Oracle, the thin driver streams in chunks equal to `fetchSize`.
For SQL Server, the `mssql-jdbc` driver also supports server-side cursors natively.

## Write batch size

`ContextProviderPort.createWriter()` accumulates rows and flushes via `executeBatch()`.
The default batch size is **500 rows**. Override via connection properties:

```yaml
connections:
  local:
    properties:
      writeBatchSize: "1000"
```

## Memory sizing

Rule of thumb: `depth × tables-per-level × avg-rows × avg-row-bytes × 2` heap headroom.

For a depth-3 clone with ~10 tables at ~500 rows and ~1 KB per row: ~30 MB peak — well within
the default JVM heap.

Set heap for the CLI with:

```bash
JAVA_OPTS="-Xmx2g" rr clone --from prod ...
```

## MongoDB

MongoDB reads use a cursor with `batchSize`. Override via connection properties:

```yaml
connections:
  mongo-prod:
    properties:
      batchSize: "200"
```

## Redis

Redis writes use synchronous Lettuce commands (`HSET` per entity). For large data sets,
consider importing from a `.rrpkg` file rather than live-cloning directly.

## Parquet (file connector)

Parquet uses columnar compression (Snappy by default). Switch to GZIP for better compression
at the cost of CPU:

```yaml
connections:
  parquet-out:
    properties:
      parquetCompression: "GZIP"
```

## Export and replay

For large clones, prefer the two-step `export → replay` flow over a direct live clone.
This lets you inspect the `.rrpkg` manifest, share the package, and replay it multiple times:

```bash
rr export --from prod --customer-id 42 --depth 5 --output ./pkg/
rr replay ./pkg/customer-42-*.rrpkg --target local
```

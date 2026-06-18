# Batch Tuning Guide

RecordRelay's engine-batch module uses Spring Batch for reliable, restartable data transfer.
This guide explains the knobs available for performance tuning.

## Key parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `batch.chunk-size` | 1 000 | Records per Spring Batch chunk (read → process → write cycle) |
| `batch.fetch-size` | 1 000 | JDBC `Statement.setFetchSize()` — controls network round-trips |
| `batch.write-batch` | 1 000 | Rows per JDBC `executeBatch()` call |
| `batch.task-executor-threads` | 1 | Parallel partitions (set > 1 for multi-table jobs) |

Configure via `application.yml` (CLI/desktop) or Spring properties (embedded):

```yaml
recordrelay:
  batch:
    chunk-size: 5000
    fetch-size: 5000
    write-batch: 5000
```

## JDBC cursor vs. paging

The JDBC reader uses a **server-side cursor** (`TYPE_FORWARD_ONLY`, `CONCUR_READ_ONLY`) with
`setFetchSize`. This streams rows without loading the whole result set into memory.

For Oracle, the thin driver streams in chunks equal to `fetch-size`.
For SQL Server, the `mssql-jdbc` driver also supports server-side cursors.

## Cassandra batch limits

Cassandra UNLOGGED batches are capped at **100 statements** by default (much lower than JDBC)
to avoid coordinator overload. Tune via:

```yaml
recordrelay.batch.cassandra-batch-size: 50
```

## Redis

Redis writes are synchronous (Lettuce sync commands). Each `HSET` is one round-trip.
For bulk loads consider using `PIPELINE` mode (not yet exposed in the public API).

## Parquet

Parquet uses columnar compression (Snappy by default). For write-heavy workloads on
HDDs, `GZIP` offers better compression at the cost of CPU:

```yaml
recordrelay.batch.parquet-compression: GZIP
```

## Memory sizing

Rule of thumb: `chunk-size × avg-row-bytes × 3` heap headroom per active job step.

For a 1 000-row chunk with ~1 KB rows: ~3 MB per step — well within the default 512 MB JVM heap.

Set JVM heap with `JAVA_OPTS=-Xmx2g rr-cli migrate ...` for large datasets.

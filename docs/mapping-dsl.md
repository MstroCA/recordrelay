# Mapping DSL

A mapping definition tells RecordRelay how to transform columns/fields between source and target.
Two dialects are supported: **SQL** and **YAML**.

## SQL dialect

```sql
-- Simple rename
SELECT id, first_name AS name, email FROM users;

-- Expression / type cast
SELECT
    id,
    CONCAT(first_name, ' ', last_name) AS full_name,
    CAST(age AS VARCHAR) AS age_str,
    email
FROM customers
WHERE active = 1;
```

The parser (`SqlMappingParser`) uses JSQLParser to extract column mappings from the `SELECT`
list. The `WHERE` clause is forwarded to the source connector's reader as a filter hint.

## YAML dialect

```yaml
mappings:
  - source: id
    target: _id
  - source: first_name
    target: name
    transform: TRIM
  - source: created_at
    target: createdAt
    transform: EPOCH_MS
```

Supported transforms:

| Transform | Description |
|-----------|-------------|
| `TRIM` | Strip leading/trailing whitespace |
| `UPPER` | Uppercase string |
| `LOWER` | Lowercase string |
| `EPOCH_MS` | Convert ISO-8601 timestamp to Unix milliseconds |
| `TO_STRING` | Call `.toString()` on the value |

## Programmatic API

```java
var mapping = new MappingDefinition(List.of(
    new ColumnMapping("user_id", "_id"),
    new ColumnMapping("first_name", "name")
));
```

If `columnMappings()` is empty, the connector reads all columns (`SELECT *` / full document).

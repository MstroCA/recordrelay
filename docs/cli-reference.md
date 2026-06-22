# CLI Reference

The `rr-cli` fat-JAR provides a command-line interface to RecordRelay.
Build it with `./gradlew :recordrelay-cli:shadowJar` or download from GitHub Releases.

## Installation

```bash
# Direct download
curl -L https://github.com/MstroCA/recordrelay/releases/latest/download/rr-cli-x.y.z.tar.gz | tar xz
export PATH="$PATH:$(pwd)/rr-cli-x.y.z/bin"

# Requirement: Java 21+
rr --version
```

## Commands

### `rr clone`

Reproduce a business context (customer, order, user…) from one environment into another.

```bash
rr clone \
  --from <conn-name> \
  [--target <conn-name>] \
  [--entity <table>] \
  [--id <pk-value>] \
  [--customer-id <id>] \
  [--order-id <id>] \
  [--user-id <id>] \
  [--product-id <id>] \
  [--invoice-id <id>] \
  [--account-id <id>] \
  [--depth <1-10>] \
  [--mask] \
  [--export] \
  [--output-dir <path>] \
  [--override key=value ...]
```

| Option | Description |
|--------|-------------|
| `--from, -f` | Source connection profile name |
| `--target, -t` | Target connection profile name (omit when `--export` is used) |
| `--entity, --table` | Entity or table name (e.g. `customer`, `orders`) |
| `--id` | Root entity primary key value |
| `--customer-id` | Shorthand — resolves to the `customers` table |
| `--order-id` | Shorthand — resolves to the `orders` table |
| `--user-id` | Shorthand — resolves to the `users` table |
| `--product-id` | Shorthand — resolves to the `products` table |
| `--invoice-id` | Shorthand — resolves to the `invoices` table |
| `--account-id` | Shorthand — resolves to the `accounts` table |
| `--depth` | Relationship traversal depth (default: 3, max: 10) |
| `--mask` | Mask common PII columns (email, phone, address, IBAN) |
| `--export` | Export to `.rrpkg` instead of writing to a live target |
| `--output-dir` | Output directory for the `.rrpkg` file |
| `--override, -o` | Field override as `table.column=value` |

---

### `rr export`

Export a business context to a `.rrpkg` reproduction package.

```bash
rr export \
  --from <conn-name> \
  --entity <table> \
  --id <pk-value> \
  [--depth <1-10>] \
  --output <dir>
```

---

### `rr import`

Import a `.rrpkg` package into a target environment.

```bash
rr import <file.rrpkg> --target <conn-name>
```

---

### `rr replay`

Replay a previously exported `.rrpkg` package.

```bash
rr replay <file.rrpkg> \
  --target <conn-name> \
  [--inspect]
```

`--inspect` prints the package manifest without writing to the target.

---

### `rr diff`

Compare a business entity context across two environments.

```bash
rr diff \
  --left <conn-name> \
  --right <conn-name> \
  [--customer-id <id>] \
  [--order-id <id>] \
  [--user-id <id>] \
  [--table <table> --id <pk>] \
  [--depth <1-10>]
```

---

### `rr discover`

Discover databases, tables, or columns for a connection.

```bash
rr discover --conn <conn-name>                          # list databases
rr discover --conn <conn-name> --database <db>          # list tables
rr discover --conn <conn-name> --database <db> --table <t>  # inspect columns
```

---

### `rr analyze`

Analyse schema compatibility between two tables across connections.

```bash
rr analyze \
  --source-conn <conn-name> \
  --source-table <schema.table> \
  --target-conn <conn-name> \
  --target-table <schema.table>
```

---

### `rr conn`

Manage connection profiles.

```bash
rr conn add --type POSTGRESQL --host <host> --port <port> \
            --database <db> --user <user>  # password prompted
rr conn list
rr conn test <conn-name>
rr conn remove <conn-name>
```

| Option | Description |
|--------|-------------|
| `--type` | DB type: `POSTGRESQL`, `MYSQL`, `SQLSERVER`, `ORACLE`, `SQLITE`, `MONGODB`, `CASSANDRA`, `REDIS`, `ELASTICSEARCH`, `FILE_CSV`, … |
| `--host` | Database host |
| `--port` | Database port |
| `--database` | Database name |
| `--user` | Database user |
| `--env` | Optional environment tag |

---

### `rr env`

Manage environment labels (group connections by environment).

```bash
rr env add <name> [--desc <description>]
rr env list
rr env remove <name>
```

---

### `rr status`

Show registered connectors and configured environments.

```bash
rr status
```

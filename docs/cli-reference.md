# CLI Reference

The `rr-cli` fat-JAR provides a command-line interface to RecordRelay.

## Installation

```bash
# macOS (Homebrew tap — coming soon)
brew install recordrelay/tap/rr-cli

# Direct download
curl -L https://github.com/your-org/recordrelay/releases/latest/download/rr-cli.tar.gz | tar xz
export PATH="$PATH:$(pwd)/rr-cli/bin"
```

## Global options

| Option | Description |
|--------|-------------|
| `--profile-store <path>` | Path to encrypted profile store (default: `~/.recordrelay/profiles.enc`) |
| `--log-level <level>` | Log verbosity: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` (default: `INFO`) |
| `-v, --version` | Print version and exit |

## Commands

### `rr-cli migrate`

Run a data migration job.

```bash
rr-cli migrate \
  --source <profile-name> \
  --source-table <schema.table> \
  --target <profile-name> \
  --target-table <schema.table> \
  [--mapping <file.sql|file.yaml>] \
  [--batch-size <n>] \
  [--dry-run]
```

### `rr-cli inspect`

Introspect a data source.

```bash
rr-cli inspect --profile <name> [--database <db>] [--table <table>]
```

### `rr-cli list-databases`

List databases/keyspaces/indices accessible via a profile.

```bash
rr-cli list-databases --profile <name>
```

### `rr-cli list-tables`

List tables/collections/indices in a database.

```bash
rr-cli list-tables --profile <name> --database <db>
```

### `rr-cli health`

Check connectivity for a profile.

```bash
rr-cli health --profile <name>
```

Output example:
```
Profile: prod-postgres
Status:  OK
Latency: 4 ms
Detail:  Reachable
```

## Profile management

Profiles are stored encrypted. Use `rr-cli profile add` to create one:

```bash
rr-cli profile add \
  --name prod-postgres \
  --type POSTGRESQL \
  --host db.prod.example.com \
  --port 5432 \
  --database myapp \
  --username appuser \
  --password  # prompted securely
```

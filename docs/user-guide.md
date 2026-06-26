# RecordRelay — User Guide

## Who is this guide for?

This guide is for anyone who needs to reproduce real database state in a local or test environment — developers debugging a production issue, QA testers who want to run tests against real-looking data, data analysts who need to share a record set with a colleague, SREs investigating an incident, or product managers exploring what a specific customer's data looks like. No deep database knowledge is required to use RecordRelay; this guide explains every concept and screen from the ground up.

---

## What is RecordRelay?

Imagine you're a developer and a customer reports a bug. The bug only happens with their specific data — a very particular combination of orders, addresses, invoices, and account settings that you can't easily reproduce by hand. RecordRelay solves this: you tell it "copy customer #12345 from production to my local database" and it does exactly that — including every order, invoice, address, and setting that belongs to that customer.

Under the hood, RecordRelay follows the links between your database tables automatically. When you clone a customer record, it finds all the orders linked to that customer, then all the order lines linked to those orders, then all the product records linked to those order lines — and so on, down however many levels you choose. You don't need to know the database structure in advance; RecordRelay discovers it for you.

RecordRelay works with 14 different data sources: all major SQL databases (PostgreSQL, MySQL, SQL Server, Oracle, SQLite), NoSQL databases (MongoDB, Cassandra, Redis, Elasticsearch), and file formats (CSV, Excel, JSON, YAML, Parquet). It runs as a desktop application, a command-line tool, or an IntelliJ IDEA plugin — whichever fits your workflow.

---

## Concepts you need to know

### Record / Row
A single item stored in a database table. For example, one customer, one order, or one product. In a spreadsheet analogy, a record is one row.

### Relationship / Foreign Key (FK)
A link between two tables. For example, an "orders" table might store the ID of the customer who placed the order. That link is a foreign key — it says "this order belongs to that customer." RecordRelay follows these links automatically to find all the data connected to the record you're cloning.

### Depth
How many "hops" of relationships RecordRelay will follow. Depth 1 means only the root record itself. Depth 2 means the root record plus any records directly linked to it. Depth 3 means one level further — the root, its direct children, and their children. Most use cases work well at depth 3 or 4.

### Clone
The act of copying a record (and all its related records, up to the configured depth) from one database to another. The source database is read-only; the target database receives the copied data.

### Environment
A named group of database connections. For example, you might define a "production" environment containing your prod PostgreSQL connection, a "staging" environment, and a "local" environment pointing to your laptop. Environments help you organize connections and quickly select source/target pairs.

### Connection
A saved set of credentials and server details for one database: host, port, database name, username, and password. You create connections once in the Connections screen and reuse them across all other screens.

### .rrpkg package
A portable file (a ZIP archive with a `.rrpkg` extension) that contains a cloned set of records. Instead of writing directly to a target database, you can export a clone to a `.rrpkg` file and share it with a colleague, who can then import it into their own environment. No live database access is needed for the recipient.

### PII / Masking
PII stands for Personally Identifiable Information — data like email addresses, phone numbers, physical addresses, IBANs, and national ID numbers. RecordRelay can automatically mask these fields before writing them to the target, replacing real values with realistic-looking fake values. Masking is deterministic: the same input always produces the same masked output, so foreign key links still work correctly.

---

## Quick Start (5 minutes)

1. **Add your source connection** — Open the **Connections** screen from the left sidebar. Click **Add…**, fill in the host, port, database name, and credentials for your source database (e.g. production), then click **Test Connection** to verify it works.

2. **Add your target connection** — Repeat step 1 for your target database (e.g. your local database).

3. **Open Clone Context** — Click **Clone Context** (Klonlama) in the left sidebar. This is the main screen.

4. **Step 1 — Select connections** — Choose your source connection from the first dropdown and your target connection from the second dropdown.

5. **Step 2 — Pick the record** — Select the root table from the dropdown (or click **Detect** to let RecordRelay suggest the most likely starting table). Enter the primary key column name and the ID of the record you want to clone.

6. **Step 3 — Set options** — Use the depth slider to choose how many relationship levels to follow (start with 3). Leave Conflict Resolution on the default **Regenerate Identities** unless you have a specific reason to change it.

7. **Click Clone Context** — Watch the log panel at the bottom. When it says "Clone complete", your record and all its related data are in the target database.

---

## Screens

### Clone Context

The Clone Context screen is the heart of RecordRelay. Everything else in the application supports this one operation: copying a real record — with all its context — from one database to another.

The screen is organized into four sequential steps.

---

#### Step 1 — Select Connection

**Source (Source Database)** — The database you are copying *from*. This database is never modified; RecordRelay only reads from it.

**Target (Target Database)** — The database you are copying *to*. This is where the cloned records will be written.

**Export mode checkbox** — When checked, RecordRelay does not write to any database. Instead, it packages the cloned records into a `.rrpkg` file saved to the folder you specify. Use this when you want to share data with a colleague who doesn't have access to your databases, or when you want to archive a snapshot of a record for later use.

---

#### Step 2 — Table & Record

**Root Table** — The table that contains the record you want to clone. When you select a source connection, RecordRelay automatically loads the list of available tables into this dropdown. If you're not sure which table to start with, click **Detect** — RecordRelay will analyze the database relationships and suggest the most likely "root" tables (the ones most other tables reference, like `customers` or `orders`).

**Primary Key Column** — The column in the root table that uniquely identifies a record. For most tables this is `id`. RecordRelay fills this in automatically when possible.

**Record ID** — The value of the primary key for the specific record you want to clone. For example, if you want to clone customer number 12345, enter `12345` here.

**Preview button** — Before running the actual clone, click Preview to see a dry-run list of which tables will be affected and how many rows will be copied. This is a safe, read-only operation.

---

#### Step 3 — Options

**Relationship depth** — The slider controls how many levels of linked tables to follow. A value of 1 copies only the root record. A value of 3 (recommended default) copies the root record, all records directly linked to it, and all records linked to those. For complex schemas, you may need depth 4 or 5; for simple ones, depth 2 is often sufficient.

**Mask PII** — When checked, RecordRelay replaces personal data (email addresses, phone numbers, physical addresses, IBANs, and national ID numbers) with realistic fake values before writing to the target. The same original value always maps to the same fake value, so relationships between records remain intact.

---

#### Conflict Resolution

The **On conflict** combobox controls what RecordRelay does when it tries to write a record to the target database and a record with the same ID already exists there. This is one of the most important settings to understand, so here is a plain-language explanation of each option.

---

**Regenerate Identities** *(default — recommended for most cases)*

RecordRelay assigns brand-new IDs to every record it clones and updates all the links between records to match the new IDs. After writing, it also advances the database's auto-increment counter so that any future records your application creates won't accidentally use an ID that RecordRelay already used.

When to use: Almost always. This is the safest option because it guarantees no collision with any existing data in the target, regardless of what is already there. Use it when your target database already has data and you don't want cloned records to interfere with it.

Example: You clone customer #12345 from production. In the target, they get a new ID — say #9001. All their orders also get new IDs, and the "customer_id" column on those orders is updated to point to #9001.

---

**Isolate Namespace**

RecordRelay offsets or prefixes all IDs so that multiple cloned contexts can coexist in the same target database without colliding with each other or with existing data.

When to use: When you need to clone several different records into the same target database at the same time and want each clone to remain independent. For example, a QA team running parallel test scenarios — each scenario gets its own isolated slice of data.

---

**Fail Safe**

RecordRelay checks the target database before writing anything. If any of the tables that would be written to already contain records, the entire operation is aborted — nothing is written.

When to use: When you want an absolute guarantee that you are working with a clean target. Useful for setting up a fresh test environment where you want to be certain no leftover data from a previous run will interfere. If the target is not empty, the operation fails immediately and clearly, rather than silently mixing old and new data.

---

**Skip Existing**

RecordRelay attempts to insert each record. If a record with the same ID already exists in the target, that record is silently skipped — the existing record is left untouched and the clone continues with the remaining records.

When to use: When you are re-running a clone of the same record (for example, refreshing data you cloned yesterday) and want only the missing records to be added. Or when cloning from one database into an identical copy of itself, where most records are already present.

Note: This does not update existing records. If the source record has changed since the last clone, those changes will not be reflected in the target.

---

#### Step 4 — Field Overrides (Optional)

The field overrides table lets you replace specific column values when writing to the target. For example:

- Change `created_by` to `test-user` so cloned records don't appear to have been created by a real production user.
- Change `status` to `DRAFT` so cloned orders don't accidentally trigger production workflows.
- Change `email` to a specific test address to prevent any system from sending emails to the real customer.

Each row in the table has three columns:
- **Table** — the table to apply the override to. Leave this empty to apply the override to every table that has a column with this name.
- **Column** — the column whose value should be replaced.
- **New Value** — the value to write to the target.

Click **+ Add** to add a new override row, and **Remove** to delete a selected row.

---

#### Common Workflows by Role

**Developer: "I need to reproduce a bug locally"**
1. Open Clone Context. Select your production database as Source, your local database as Target.
2. Set Root Table to the table most relevant to the bug (e.g. `orders`). Enter the ID of the order that triggers the bug.
3. Set depth to 3 or 4 to capture enough context.
4. Leave Conflict Resolution on **Regenerate Identities**.
5. Optionally enable **Mask PII** if you don't need real personal data to reproduce the bug.
6. Click **Clone Context**. Open your debugger.

**QA Tester: "I need to test with real production-like data"**
1. Open Clone Context. Select production as Source, your test environment as Target.
2. Enable **Mask PII** — always mask when copying to a test environment.
3. Set depth to 3. Use **Regenerate Identities** so test data doesn't collide with anything already in the test database.
4. Optionally add a Field Override: set `email` to `qa-test@example.com` so no real emails are triggered.
5. Clone. Then run your test suite against the freshly populated test database.

**Analyst: "I need to export data to share with a colleague"**
1. Open Clone Context. Select your source database.
2. Check **Export mode** and choose an output folder.
3. Select the root table and record ID.
4. Enable **Mask PII** if the colleague shouldn't see real personal data.
5. Click **Export Package**. Share the `.rrpkg` file. Your colleague can import it via **File → Import Package** or the CLI (`rr import`).

---

### Environments

The Environments screen lets you organize your database connections into named groups. For example, you might create three environments: "Production", "Staging", and "Local Development". Within each environment, you assign one or more connections.

Environments make it faster to find the right connection pair when setting up a clone — instead of scrolling through a long list of individual connections, you can filter by environment.

To create an environment, click **Add…**, give it a name and an optional description, and save. To assign connections to an environment, edit the environment and select the connections that belong to it.

---

### Connections

The Connections screen is where you store the credentials and server details for every database you work with. Each connection has:

- **Name** — a human-readable label (e.g. "prod-postgres", "local-mysql")
- **Type** — the database engine (PostgreSQL, MySQL, MongoDB, etc.)
- **Host** — the server address
- **Port** — the port number (defaults are filled in automatically per database type)
- **Database** — the specific database/schema name
- **User** and **Password** — credentials

Click **Test Connection** to verify that RecordRelay can reach the database before saving. Connections are stored locally and never transmitted anywhere.

---

### Discovery

The Discovery screen lets you explore the structure of any connected database without writing any queries. Select a connection, choose a database, then pick a table and click **Inspect Columns** to see all column names, data types, whether each column is nullable, and which column is the primary key.

This is useful before running a clone when you want to confirm the correct table name and primary key column name. It is also useful for analysts who want to understand what data is available before writing a query.

---

### Monitor / Dashboard

The Monitor screen gives you a session-wide view of everything that has happened since you opened the application.

**Clone History** — a table showing every clone operation you have run in this session, including the root table, the record ID, source, target, how many records were transferred, how long it took, and whether it succeeded or failed.

**Summary chips** — at the top, quick statistics: total records transferred, number of operations, failures, and the duration of the last operation.

**Connection Health** — a table showing the current reachability and latency of all your saved connections. Click **Check Health** to refresh all status checks.

Use the **Clear History** button to reset the session log without restarting the application.

---

### Graph View

The Graph View renders your database schema as a visual relationship map — similar to an Entity-Relationship Diagram (ERD) but focused on showing which tables are connected to which, and how.

**How to use it:**
1. Select a connection from the toolbar dropdown.
2. Choose a root table — the table from which you want to explore relationships.
3. Click **Discover**. RecordRelay will query the database's metadata and draw the relationship graph.

**Reading the graph:**
- Each box (node) represents one table.
- Lines (edges) between boxes represent relationships. Solid lines are declared foreign keys; dashed lines are heuristic links RecordRelay inferred from column naming patterns (e.g. a column named `customer_id` that points to a `customers` table).
- Hover over any node to highlight only the edges connected to it, making it easier to trace one table's relationships.

**Navigation:**
- Use the **Zoom** slider or **+/−** buttons to zoom in and out.
- Hold **Ctrl** and scroll the mouse wheel to zoom with the mouse.
- Click **Fit to Screen** to reset the zoom so all nodes are visible.

The Graph View is particularly useful for understanding an unfamiliar database before deciding on a depth setting for cloning.

---

### Migration Drift

The Migration Drift screen detects structural differences between two databases. It is useful when you want to verify that your staging database has the same schema as production, or when you are planning a migration and want to know exactly what has changed.

**How to use it:**
1. Select a source connection and database (typically production — the reference).
2. Select a target connection and database (typically staging or local — the one to check).
3. Click **Analyze**.

The results table shows each difference found:
- **Missing Table** — exists in source but not in target
- **Extra Table** — exists in target but not in source
- **Missing Column** — a column present in the source table is absent from the target
- **Extra Column** — the target table has a column the source does not
- **Type Mismatch** — both tables have the column but the data type differs

The **Generate SQL** button at the bottom produces a SQL patch script that would bring the target schema in line with the source. Safe changes are generated as plain SQL; risky changes (like dropping a column) are commented out so you can review them manually before running.

---

### Row Count Diff

The Row Count Diff screen compares how many records each table contains between two databases. It does not look at the content of the records — only the counts.

This is useful for spotting data divergence: if production has 50,000 orders and your staging database has only 48,000, something went wrong in the last sync.

**Statuses per table:**
- **In sync** — both databases have the same count
- **Target behind** — the target has fewer records than the source
- **Target ahead** — the target has more records than the source

The summary chips at the top give a quick count of how many tables fall into each category.

---

### Query Analyzer

The Query Analyzer lets you run queries against any connected database and see the results in a table. It offers two modes:

**Flow mode (visual query builder)**
Build queries by dragging and dropping — no SQL knowledge required.
1. Select a connection and database from the toolbar.
2. Double-click any table in the left panel to add it to the canvas.
3. Click the round port button next to a column to start a JOIN, then click a port on another table to complete it. A dialog asks whether you want an INNER, LEFT, or RIGHT join.
4. Check or uncheck column checkboxes to control which columns appear in the results.
5. Use the right panel to add WHERE filters, ORDER BY clauses, and a row limit.
6. The generated SQL appears in a preview panel and updates as you build the query.
7. Click **Run** to execute and see results.

**SQL mode (raw editor)**
Type any SELECT query directly and click Run. Use this when you already know the SQL you want to run.

The Query Analyzer is read-only — it cannot modify data.

---

### Connection Health

The Connection Health screen pings all your saved connections and reports back their status. Click **Test All** to run the check.

For each connection you see:
- **Status** — whether the connection succeeded or failed
- **Latency** — how long the connection attempt took (in milliseconds)
- **Detail** — any error message if the connection failed

Use this screen to quickly verify that all your databases are reachable before starting a clone operation, or to diagnose why a connection is failing.

---

### Masking Coverage

The Masking Coverage screen scans a database schema and identifies columns that likely contain personal data, then shows whether each column is covered by a masking rule.

Click **Scan** after selecting a source connection to run the analysis. The results table shows each column RecordRelay considers a PII risk, its detected category (EMAIL, PHONE, ADDRESS, IBAN, NATIONAL_ID), and whether it would be masked if you ran a clone with **Mask PII** enabled.

Use the **Show exposed only** checkbox to filter the list to columns that are not yet covered by masking rules, so you can identify gaps.

This screen is useful for compliance reviews — it gives you a quick answer to "if we clone this database, which personal data columns would be exposed in the target?"

---

### Clone Presets

The Clone Presets screen lets you save a complete clone configuration — source, target, root table, ID, depth, masking settings, and field overrides — so you can re-run it with a single click instead of filling out the Clone Context form every time.

To save a preset:
1. Fill in all the fields in the **Save New Preset** section at the bottom of the screen.
2. Give it a descriptive name (e.g. "Daily prod customer refresh").
3. Click **Save Preset**.

The preset appears in the list at the top. To run it, select it and click **Run Now**. The log panel shows the output in real time.

Presets are useful for repetitive tasks like refreshing test data every morning before the QA team starts work.

---

### Scheduled Sync

The Scheduled Sync screen extends Clone Presets with a scheduling layer. You define a sync (same parameters as a preset), plus a schedule — either a cron expression (for fine-grained control) or a simple interval in minutes.

**Cron expression** — for example, `0 6 * * 1-5` means "run at 6:00 AM on every weekday." Standard five-field cron syntax.

**Interval** — for example, `60` means "run every 60 minutes." Simpler than cron but less flexible.

Leave both blank to create a manual-only sync — one you can trigger with **Run Now** but that does not run automatically.

Scheduled syncs are useful for keeping a staging or test environment continuously topped up with fresh production data, without anyone having to remember to run a clone manually.

---

## CLI Reference

The `rr` command-line tool supports all core RecordRelay operations from a terminal or CI/CD pipeline. Here are the most common commands:

```bash
# Add a connection
rr conn add --name prod --type POSTGRESQL --host db.prod --port 5432 --database mydb --user admin

# Clone a record
rr clone --entity customer --id 12345 --from prod --target local --depth 3

# Clone with PII masking
rr clone --entity customer --id 12345 --from prod --target local --depth 3 --mask-pii

# Export to a .rrpkg file
rr export --entity customer --id 12345 --from prod --output ./exports/

# Import a .rrpkg file
rr import customer-12345-1234567890.rrpkg --target local

# Compare two environments
rr diff --entity customer --id 12345 --from prod --to staging

# Explore schema
rr discover --source prod
```

For the full list of commands, flags, and examples, see [CLI Reference](cli-reference.md).

---

## Role-Based Workflows

### As a QA Tester

Your goal is to test application behavior against realistic data without using real production user data directly.

**Setting up a test database before a sprint:**
1. Make sure your test database is accessible as a saved connection.
2. Open **Clone Presets** and create a preset for each common test scenario (e.g. "Active customer with orders", "Customer with failed payment", "New customer with no orders").
3. For each preset, enable **Mask PII** and consider adding a Field Override to set `email` to a test address.
4. Each morning, run all presets with **Run Now** to refresh the test data.

**Reproducing a specific bug:**
1. Get the production record ID from the bug report.
2. Open **Clone Context**, source = production, target = your test environment.
3. Enable **Mask PII**, set an appropriate depth, choose **Regenerate Identities**.
4. Clone. Open the application in your test environment. Reproduce the bug.

**Before reporting "this works in test but not in prod":**
1. Check **Row Count Diff** — are the table counts similar? A large discrepancy might mean your test data is incomplete.
2. Check **Migration Drift** — does the test database schema match production? Schema differences can cause behavior differences.

---

### As a Developer

Your goal is to reproduce issues locally or set up realistic data for local development without manually crafting INSERT statements.

**Reproducing a production bug locally:**
1. Open **Clone Context**, source = production, target = local.
2. Use the record ID from the bug report.
3. Set depth to 3–4 to capture enough context.
4. Enable **Mask PII** if you don't need real personal data.
5. Choose **Regenerate Identities** so the cloned records don't collide with anything you already have locally.
6. Clone. Attach your debugger.

**Understanding an unfamiliar schema:**
1. Open **Graph View**, select your source connection.
2. Set a table as the root and click **Discover**.
3. Use hover highlighting to trace which tables are connected to which.
4. Use **Discovery** to inspect individual table column definitions.

**Checking if your local schema is up to date:**
1. Open **Migration Drift**, source = production, target = local.
2. Click **Analyze**.
3. If differences appear, click **Generate SQL** and review the patch script.

---

### As a Data Analyst

Your goal is to get access to specific data for analysis without needing to write complex SQL or request database access.

**Getting a data snapshot to work with:**
1. Open **Clone Context**, source = your data source, target = a local database or file export.
2. Enable **Mask PII** if required by your data governance policy.
3. Check **Export mode** to save as a `.rrpkg` file if you want to work offline or share with a colleague.
4. Clone or export.

**Exploring what data is available:**
1. Open **Discovery**, select your connection.
2. Browse through databases and tables.
3. Click **Inspect Columns** to see the structure of any table.
4. Switch to **Query Analyzer** to run exploratory queries against live data.

**Sharing data with a colleague:**
1. Use **Export mode** in Clone Context to produce a `.rrpkg` file.
2. Send the file to your colleague.
3. They import it via `rr import file.rrpkg --target local` or via the desktop app.

---

### As an SRE / DevOps Engineer

Your goal is to automate data reproduction as part of your environment management workflows.

**Keeping staging fresh automatically:**
1. Open **Scheduled Sync** and create a new sync.
2. Set source = production, target = staging.
3. Enable **Mask PII** (required before writing to any non-production environment).
4. Set a cron schedule: `0 3 * * *` (3 AM daily).
5. Save. Staging will be topped up with fresh production data every night.

**Integrating into CI/CD:**
Use the CLI. Add a step to your pipeline that clones a known-good test record before running integration tests:
```bash
rr clone --entity order --id 99 --from prod --target ci-db --depth 3 --mask-pii --conflict REGENERATE_IDENTITIES
```

**Diagnosing data divergence:**
1. Open **Row Count Diff**, source = production, target = staging.
2. Click **Compare**.
3. Any table where the counts diverge significantly is worth investigating.

**Pre-deployment schema validation:**
1. Open **Migration Drift**, source = the environment you are deploying to, target = the environment running the new code.
2. Analyze. If the patch script is non-trivial, review it before deploying.

---

## FAQ

**Q: What does "depth" mean, exactly?**
Depth controls how many levels of linked tables RecordRelay will follow. If you clone a customer at depth 1, you get only the customer record. At depth 2, you also get all records in other tables that directly reference that customer (e.g. their addresses, their orders). At depth 3, you get those records plus everything that references *them* (e.g. the order lines within the orders, the order history events). Think of it like exploring a web of connections outward from the record you picked, stopping after N hops.

**Q: When should I use FAIL_SAFE instead of REGENERATE_IDENTITIES?**
Use FAIL_SAFE when you are setting up a completely clean environment and want a hard guarantee that no existing data will be mixed in. It's appropriate for initial environment setup scripts or for automated test pipelines that reset the database before each run. If there's any chance the target already has data in the relevant tables, FAIL_SAFE will abort — which is exactly what you want in that scenario, because it prevents silent data corruption.

**Q: When should I use SKIP_EXISTING?**
Use SKIP_EXISTING when you are refreshing data you've already cloned once and only want to add records that are missing. For example, you cloned a customer yesterday, new orders have been placed since then, and you want to add only the new orders without touching the ones already in the target. Note that SKIP_EXISTING does not update existing records — if the customer's address changed, the old address stays.

**Q: What exactly is a .rrpkg file?**
A `.rrpkg` file is a ZIP archive containing all the cloned records in a structured format, plus a manifest describing the schema, the source, and the relationships. It's self-contained — you can share it via email, a file share, or a ticket attachment, and the recipient can import it without any access to your databases. The format version is v2.1 and is backward-compatible across RecordRelay releases.

**Q: Can I mask data? What gets masked?**
Yes. Enable the **Mask PII** checkbox in Step 3. RecordRelay automatically detects and masks columns whose names match patterns for EMAIL, PHONE, ADDRESS, IBAN, and NATIONAL_ID. Masking is deterministic: the same original value always produces the same masked value, so foreign key links between records remain valid. For example, if ten order records all reference the same customer email, the masked records will all reference the same masked email.

**Q: Will cloning affect the source database?**
No. RecordRelay only reads from the source. It uses a read-only connection and never writes to, modifies, or locks the source database in any way that would affect other users.

**Q: My clone succeeded but some tables show 0 records written. Why?**
This is normal. RecordRelay follows all the relationships it discovers, but many related tables may simply have no records for the entity you cloned. For example, if you clone a customer who has no failed payments, the `failed_payments` table will show 0 records — because there are none to copy. The 0 is informational, not an error.

**Q: The clone failed with a warning about a type mismatch. What do I do?**
This usually means the source and target databases have a small schema difference — for example, a column is an enum type in the source but a plain text column in the target. Open **Migration Drift** to identify the exact difference, generate the SQL patch script, and apply it to the target database to align the schemas. Then re-run the clone.

**Q: Can I clone across different database types (e.g. PostgreSQL to MySQL)?**
Yes. RecordRelay uses a database-agnostic intermediate representation, so you can clone from PostgreSQL to MySQL, from MySQL to SQLite, from a CSV file into a database, and so on. Type mappings are handled automatically. Complex type-specific features (like PostgreSQL enum types, arrays, or JSON columns) are mapped to the closest equivalent in the target.

**Q: How do I add a new database connection type that isn't listed?**
RecordRelay uses a plugin architecture. You can add a new connector by implementing the `ContextProviderPort` interface and registering it via Java's ServiceLoader mechanism. See the `recordrelay-adapter-template` module for a starter template. Once the JAR is on the classpath, RecordRelay discovers it automatically.

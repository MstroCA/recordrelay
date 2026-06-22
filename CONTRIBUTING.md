# Contributing to RecordRelay

Thank you for contributing to RecordRelay! This document describes the development process, code standards, and steps for writing new connectors.

---

## Table of Contents

1. [Development Environment](#development-environment)
2. [Project Structure](#project-structure)
3. [Code Standards](#code-standards)
4. [Writing Tests](#writing-tests)
5. [Writing a New Connector](#writing-a-new-connector)
6. [PR Process](#pr-process)
7. [Commit Messages](#commit-messages)

---

## Development Environment

### Requirements
- Java 21 (Temurin recommended)
- Docker Desktop (for Testcontainers)
- IntelliJ IDEA or any Java IDE

### Initial Setup

```bash
git clone https://github.com/recordrelay/recordrelay.git
cd recordrelay
./gradlew build           # Compile + all checks
./gradlew test            # Unit tests only
./gradlew integrationTest # Testcontainers integration tests (requires Docker)
```

### Quality Tools

| Tool | Purpose | Command |
|------|---------|---------|
| Spotless | Code formatting (Google Java Format) | `./gradlew spotlessApply` |
| Checkstyle | Structural rules | `./gradlew checkstyleMain` |
| SpotBugs | Static analysis, bug patterns | `./gradlew spotbugsMain` |
| JaCoCo | Code coverage (70% minimum) | `./gradlew jacocoTestReport` |

Before opening a PR, run:
```bash
./gradlew spotlessApply && ./gradlew check
```

---

## Project Structure

```
core/                    ← No adapter code goes here
  domain/                ← Immutable business objects (record/immutable)
  port/in/               ← Driving ports (use-case interfaces)
  port/out/              ← Driven ports (connector interfaces)
  spi/                   ← ServiceLoader registry
  engine/                ← Transfer orchestration

connectors/
  connector-{db}/        ← Separate module per database
    src/main/java/...    ← Classes implementing the ports
    src/main/resources/
      META-INF/services/ ← SPI registration files
    src/test/java/...    ← Unit + Testcontainers tests
```

---

## Code Standards

- **Java 21**: Records, sealed interfaces, and pattern matching are preferred.
- **Immutability**: Domain objects must be `record` types or plain immutables (no Lombok).
- **Null safety**: Use `Optional`; never return or accept `null`.
- **Exception handling**: Checked exceptions must be wrapped as `ConnectorException` (unchecked) at the connector boundary.
- **Javadoc**: Required for all `public` APIs. Document the *why*, not the *what*.
- **Comments**: Only for non-obvious constraints or workarounds. "This method does X" comments are not accepted.
- **Method length**: Max 60 lines (enforced by Checkstyle).
- **Cyclomatic complexity**: Max 10 (enforced by Checkstyle).

### Package Structure

```
io.recordrelay.core.domain.*
io.recordrelay.core.port.in.*
io.recordrelay.core.port.out.*
io.recordrelay.core.spi.*
io.recordrelay.core.engine.*
io.recordrelay.connector.postgresql.*
io.recordrelay.connector.mongodb.*
io.recordrelay.cli.*
io.recordrelay.desktop.*
io.recordrelay.plugin.*
```

---

## Writing Tests

### Unit Test

```java
@ExtendWith(MockitoExtension.class)
class TransferEngineTest {

    @Mock
    private RecordReader reader;

    @Mock
    private RecordWriter writer;

    @InjectMocks
    private DefaultTransferEngine engine;

    @Test
    void shouldTransferAllRecords() {
        // Arrange
        var job = TransferJob.builder()...build();
        // Act + Assert (AssertJ)
        assertThat(result.transferredCount()).isEqualTo(100);
    }
}
```

### Integration Test (Testcontainers)

```java
@Testcontainers
@Tag("integration")
class PostgreSqlConnectorIT {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void shouldListDatabases() {
        var connector = new PostgreSqlConnector(buildProfile(postgres));
        assertThat(connector.listDatabases()).isNotEmpty();
    }
}
```

Integration tests live in the `integrationTest` source set and are run separately with `./gradlew integrationTest`.

---

## Writing a New Connector

### 1. Create the Module

Create a new Gradle subproject under `connectors/`:

```
connectors/connector-{dbname}/
  build.gradle.kts
  src/main/java/io/recordrelay/connector/{dbname}/
    {Dbname}Connector.java         ← DataSourceConnector impl
    {Dbname}SchemaInspector.java   ← SchemaInspector impl
    {Dbname}RecordReader.java      ← RecordReader impl
    {Dbname}RecordWriter.java      ← RecordWriter impl
  src/main/resources/META-INF/services/
    io.recordrelay.core.port.out.DataSourceConnector
```

### 2. Implement the Ports

```java
public final class MyDbConnector implements DataSourceConnector {

    @Override
    public String connectorId() {
        return "mydb"; // unique identifier
    }

    @Override
    public boolean supports(ConnectionProfile profile) {
        return "mydb".equals(profile.type());
    }

    @Override
    public Connection connect(ConnectionProfile profile) throws ConnectorException {
        // ...
    }
}
```

### 3. Register the SPI

Create `src/main/resources/META-INF/services/io.recordrelay.core.port.out.DataSourceConnector`:

```
io.recordrelay.connector.mydb.MyDbConnector
```

`ConnectorRegistry` discovers this class automatically via `ServiceLoader`.

### 4. Add to `settings.gradle.kts`

```kotlin
include("connectors:connector-mydb")
```

### 5. Test

Write an integration test that spins up a real DB container using Testcontainers.

---

## PR Process

1. Fork or create a feature branch from `develop`: `feature/connector-cassandra`
2. Make your changes and pass the tests: `./gradlew check`
3. Fill in the PR template when opening: what changed, why, how it was tested.
4. At least 1 reviewer approval is required.
5. CI must be green on all matrix targets (Linux/macOS/Windows).
6. Squash merge — keeps the commit history clean.

### Branch Naming

```
feature/connector-cassandra
fix/postgresql-schema-introspection
chore/update-dependencies
docs/connector-guide
```

---

## Commit Messages

[Conventional Commits](https://www.conventionalcommits.org/) format:

```
feat(connector-postgresql): add schema introspection for partitioned tables
fix(core): handle null column default values in ColumnMeta
chore(deps): upgrade Testcontainers to 1.20.0
docs(contributing): add connector writing guide
test(connector-mongodb): add collection listing integration test
```

Types: `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`, `ci`

---

## License

All contributions are accepted under the [Apache-2.0](LICENSE) license. By contributing, you agree that your code will be published under this license.

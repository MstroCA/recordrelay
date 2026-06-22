# Plugin Development Guide

The `recordrelay-plugin` module is an IntelliJ Platform plugin built with the
[IntelliJ Platform Gradle Plugin v2](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

## Building

```bash
./gradlew :recordrelay-plugin:buildPlugin       # produces plugin ZIP
./gradlew :recordrelay-plugin:runIde            # launch a sandboxed IDE with the plugin loaded
./gradlew :recordrelay-plugin:runPluginVerifier # verify compatibility with target IDE versions
```

## Plugin structure

```
recordrelay-plugin/src/main/java/io/recordrelay/plugin/
  RecordRelayPlugin.java                  ← Plugin entry point (no-op, wired via plugin.xml)
  service/
    RecordRelayService.java               ← @Service(APP) — ConfigStore, ConnProfileResolver,
                                              ConnectorRegistry classloader reload
  toolwindow/
    RecordRelayToolWindowFactory.java     ← Creates the tool window with four tabs
    CloneContextPanel.java                ← DB-driven clone / export tab
    ConnectionsPanel.java                 ← Connection health check tab
    DiscoveryPanel.java                   ← Column inspection + compatibility map tab
    MonitorPanel.java                     ← Clone history dashboard + KPI metrics tab
```

## Extension points (plugin.xml)

| Extension point | Implementation | Purpose |
|-----------------|----------------|---------|
| `com.intellij.toolWindow` | `RecordRelayToolWindowFactory` | Side panel with four tabs: Clone, Connections, Discovery, Monitor |
| `com.intellij.applicationService` | `RecordRelayService` | Shared ConfigStore, ConnProfileResolver, and ConnectorRegistry bootstrap |

## RecordRelayService

`RecordRelayService` is the application-level singleton. It is instantiated once by IntelliJ
and accessible via `RecordRelayService.getInstance()`.

Key responsibilities:
- On construction, calls `ConnectorRegistry.reloadFrom(RecordRelayService.class.getClassLoader())`
  so that adapter JARs bundled inside the plugin sandbox are discoverable via `ServiceLoader`.
- Lazily initialises `ConfigStore` (reads `~/.recordrelay/`) and `ConnProfileResolver`.

```java
var profile = RecordRelayService.getInstance().resolver().resolve("local-db-1");
```

## Classloader isolation

The IntelliJ plugin sandbox isolates the plugin classloader from the platform classloader.
Two issues arise from this:

1. **Connector discovery** — `ServiceLoader.load(ContextProviderPort.class)` uses the calling
   class's classloader. At static-init time this is the platform CL, which cannot see adapter
   JARs inside the plugin. Fixed by `ConnectorRegistry.reloadFrom(pluginCL)` in
   `RecordRelayService` constructor.

2. **JDBC driver loading** — HikariCP resolves the JDBC driver via `DriverManager`, which uses
   the thread context classloader (TCCL). In IntelliJ, the TCCL is the platform CL at
   connection time. Fixed by temporarily swapping the TCCL to the factory class's own CL
   before constructing `HikariDataSource` in `DataSourceFactory` and `JdbcDataSourceFactory`.

## Adding a new tab

1. Create a `JPanel` subclass in `toolwindow/`.
2. Add it as a new tab in `RecordRelayToolWindowFactory.createToolWindowContent()`.
3. Use `ProgressManager.getInstance().run(new Task.Backgroundable(...))` for all blocking
   calls to avoid freezing the EDT.

## Signing and publishing

Signing and publishing are handled by GitHub Actions (`publish-plugin.yml`). Required secrets:

| Secret | Description |
|--------|-------------|
| `CERTIFICATE_CHAIN` | PEM certificate chain from JetBrains Marketplace |
| `PRIVATE_KEY` | PEM private key |
| `PRIVATE_KEY_PASSWORD` | Private key passphrase |
| `PUBLISH_TOKEN` | JetBrains Marketplace API token |

To publish manually:

```bash
export CERTIFICATE_CHAIN=$(cat chain.pem)
export PRIVATE_KEY=$(cat key.pem)
export PRIVATE_KEY_PASSWORD=secret
export PUBLISH_TOKEN=your-marketplace-token
./gradlew :recordrelay-plugin:publishPlugin
```

## Compatibility targets

The plugin targets IntelliJ IDEA 2024.1+ (`sinceBuild = "241"`).
Run `./gradlew :recordrelay-plugin:runPluginVerifier` to validate against the declared range.

# Plugin Development Guide

The `plugin` module is an IntelliJ Platform plugin built with the
[IntelliJ Platform Gradle Plugin v2](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

## Building

```bash
./gradlew :plugin:buildPlugin       # produces plugin ZIP
./gradlew :plugin:runIde            # launch a sandboxed IDE with the plugin loaded
./gradlew :plugin:runPluginVerifier # verify compatibility with target IDE versions
```

## Extension points

The plugin contributes the following IntelliJ extension points:

| Extension point | Implementation | Purpose |
|-----------------|---------------|---------|
| `com.intellij.toolWindow` | `RecordRelayToolWindow` | Side panel with profile list and job runner |
| `com.intellij.actionGroup` | `RecordRelayActionGroup` | "Tools > RecordRelay" menu |
| `com.intellij.applicationService` | `ProfileStoreService` | Persistent encrypted profile storage |

## Adding a new extension point

1. Declare it in `plugin/src/main/resources/META-INF/plugin.xml`.
2. Implement the corresponding IntelliJ `Extension` interface.
3. Register via the `intellijPlatform.pluginConfiguration` block in `plugin/build.gradle.kts`.

## Signing and publishing

Signing and publishing are done via GitHub Actions (`publish-plugin.yml`). Secrets required:

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
./gradlew :plugin:publishPlugin
```

## Compatibility targets

The plugin targets IntelliJ IDEA 2024.1+ (`sinceBuild = "241"`).
Run `./gradlew :plugin:runPluginVerifier` to validate against the declared compatibility range.

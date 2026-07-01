# Distributing plugin updates (self-hosted)

When RecordRelay is installed from a downloaded `.zip` (not the JetBrains
Marketplace), IntelliJ IDEA has **no update channel** for it: a newer build only
appears after you *uninstall* the old one and install the new zip. That is the
symptom of a missing **custom plugin repository descriptor** (`updatePlugins.xml`).

To get real "Update available" notifications, host two files at a stable URL and
register that URL as a plugin repository.

## 1. Build the artifacts

```bash
# builds build/distributions/RecordRelay-<version>.zip
./gradlew :recordrelay-plugin:buildPlugin

# writes build/distributions/updatePlugins.xml describing that zip
./gradlew :recordrelay-plugin:generateUpdatePluginsXml \
    -PpluginRepoBaseUrl=https://downloads.example.com/recordrelay
```

`pluginRepoBaseUrl` is the directory URL where you will publish the zip. The
generated descriptor looks like:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugins>
  <plugin id="io.recordrelay.plugin"
          url="https://downloads.example.com/recordrelay/RecordRelay-1.2.2.zip"
          version="1.2.2">
    <idea-version since-build="241" until-build="262.*"/>
  </plugin>
</plugins>
```

The `version` and `idea-version` range are taken from the plugin build config, so
they always match the manifest.

## 2. Publish

Upload **both** files to that directory so these URLs resolve:

```
https://downloads.example.com/recordrelay/updatePlugins.xml
https://downloads.example.com/recordrelay/RecordRelay-<version>.zip
```

Any static host works (S3/GCS bucket, GitHub Pages, nginx, an internal file
server). Keep the same `updatePlugins.xml` URL across releases and just bump its
`version`/`url` each time (re-run `generateUpdatePluginsXml` per release).

## 3. Register the repository in IDEA (once per machine)

`Settings → Plugins → ⚙ (gear) → Manage Plugin Repositories… → +` and add:

```
https://downloads.example.com/recordrelay/updatePlugins.xml
```

From then on IDEA polls that descriptor and shows RecordRelay updates inline —
no uninstall/reinstall. Users update with one click, same as a Marketplace plugin.

## Release checklist

1. Bump the version (git tag, or `-PreleaseVersion=x.y.z`).
2. `./gradlew :recordrelay-plugin:buildPlugin`
3. `./gradlew :recordrelay-plugin:generateUpdatePluginsXml -PpluginRepoBaseUrl=<base>`
4. Upload the new `RecordRelay-x.y.z.zip` **and** the regenerated `updatePlugins.xml`,
   overwriting the previous `updatePlugins.xml`.

> Prefer the official channel? Publishing to the JetBrains Marketplace
> (`./gradlew :recordrelay-plugin:publishPlugin`) gives updates automatically and
> makes this self-hosted descriptor unnecessary.

plugins {
    alias(libs.plugins.intellij.platform)
}

// dnsjava (transitive from cassandra-driver) registers a broken InetAddressResolverProvider
// that crashes JaCoCo's JVM agent startup. Exclude it project-wide in this module.
configurations.all {
    exclude(group = "dnsjava", module = "dnsjava")
}

description = "RecordRelay IntelliJ IDEA plugin"

// Shared IDE compatibility range — referenced by both the plugin manifest and the
// custom-repository updatePlugins.xml so the two never drift apart.
val pluginSinceBuild = "241"
val pluginUntilBuild = "262.*"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "io.recordrelay.plugin"
        name = "RecordRelay"
        version = project.version.toString()
        description =
            """
            <h2>Clone database records with their full relationship graph — without leaving IntelliJ</h2>

            <p>
              RecordRelay reproduces a single business record and its entire data context (the
              foreign-key dependency tree) from one database environment to another. Pick a root
              table, enter an ID, click <em>Clone Context</em> — the engine traverses every FK link
              and writes all dependent rows to the target in the correct insert order.
            </p>

            <h3>Features</h3>
            <ul>
              <li><b>Context Cloning</b> — Copies a record and every FK-linked row it depends on,
                  across environments, in the right order, in seconds.</li>
              <li><b>Conflict Resolution</b> — Four strategies for cloning into non-empty targets:
                  <em>Regenerate Identities</em> (new IDs), <em>Isolate Namespace</em> (1 M+ offset),
                  <em>Skip Existing</em> (silently skips duplicates), and <em>Fail Safe</em>
                  (aborts if the target already has data).</li>
              <li><b>Automatic FK Discovery</b> — Discovers both declared and logical (implicit)
                  foreign-key relationships automatically, including FK-free schemas that rely on
                  naming conventions such as <code>customer_id</code>.</li>
              <li><b>Relationship Graph View</b> — Visualises the full FK dependency graph of the
                  cloned context so you can see exactly which tables and rows are involved.</li>
              <li><b>PII Masking</b> — Scrubs email, phone, IBAN, national ID, and address
                  columns on the fly during transfer.</li>
              <li><b>Field Overrides</b> — Override any column value in the target
                  (e.g. <code>created_by=test</code> or <code>orders:status=PENDING</code>).</li>
              <li><b>Clone History</b> — Browse per-session clone history with table-level record
                  counts, elapsed time, and connection health at a glance.</li>
              <li><b>Export Packages</b> — Export the context as a portable <code>.rrpkg</code>
                  file to share with a teammate or import later.</li>
              <li><b>Schema Discovery</b> — Inspect column types and analyse cross-database type
                  compatibility side by side.</li>
              <li><b>Query Runner</b> — Execute raw SQL against any configured connection from
                  inside the IDE.</li>
            </ul>

            <h3>How It Works</h3>
            <ol>
              <li>Add your database connections in the <em>Connections</em> tab.</li>
              <li>Switch to <em>Clone</em>, choose source and target environments.</li>
              <li>Select the root table, enter the record ID, and pick a conflict resolution strategy.</li>
              <li>Click <em>Clone Context</em> — RecordRelay fetches all related rows and inserts
                  them into the target in dependency order.</li>
            </ol>

            <h3>Supported Databases</h3>
            <p>
              PostgreSQL &nbsp;·&nbsp; MySQL &nbsp;·&nbsp; MariaDB &nbsp;·&nbsp; SQL Server
              &nbsp;·&nbsp; Oracle &nbsp;·&nbsp; MongoDB &nbsp;·&nbsp; SQLite &nbsp;·&nbsp;
              Cassandra &nbsp;·&nbsp; Redis &nbsp;·&nbsp; Elasticsearch
            </p>

            <h3>Typical Use Cases</h3>
            <ul>
              <li>Reproduce a production bug locally with real, complete data.</li>
              <li>Seed a staging environment for a specific test scenario.</li>
              <li>Share a precise data context with a colleague as a package file.</li>
              <li>Validate a migration script against real production relationships.</li>
            </ul>

            <p><em>Requires IntelliJ IDEA 2024.1 or later (Community or Ultimate).</em></p>
            """.trimIndent()
        changeNotes =
            """
            <h3>1.2.0</h3>
            <ul>
              <li>Added four conflict resolution modes: Regenerate Identities, Isolate Namespace,
                  Skip Existing, and Fail Safe.</li>
              <li>Hybrid BFS traversal: outgoing FK edges use the full schema graph to fetch all
                  dependencies; incoming edges use the conservative root-centric graph to prevent
                  scope creep.</li>
              <li>FK remapping now covers cross-table columns (e.g. <code>orders.created_by</code>)
                  that are outside the root-centric traversal graph.</li>
              <li>Heuristic FK discovery now matches both singular (<code>customer_id</code>) and
                  plural (<code>customers_id</code>) column naming, covering FK-free schemas.</li>
            </ul>
            <h3>1.1.0</h3>
            <ul>
              <li>Clone history dashboard with per-session record counts and elapsed time.</li>
              <li>Relationship graph view for the cloned FK context.</li>
              <li>In-app help screen with quick-start guide and keyboard shortcuts.</li>
              <li>Field overrides: override any column value before writing to the target.</li>
            </ul>
            """.trimIndent()
        ideaVersion {
            sinceBuild = pluginSinceBuild
            untilBuild = pluginUntilBuild
        }
    }
    // pluginVerification: run './gradlew :recordrelay-plugin:runPluginVerifier' manually.
    // Requires network access; not wired into the standard check task.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3")
        pluginVerifier()
        zipSigner()
        instrumentationTools()
    }
    implementation(project(":recordrelay-core"))
    implementation(project(":recordrelay-domain"))
    implementation(project(":recordrelay-cli"))
    implementation(project(":recordrelay-engine"))
    implementation(libs.jackson.databind)
    runtimeOnly(project(":recordrelay-graph-engine"))
    runtimeOnly(project(":recordrelay-masking-engine"))
    runtimeOnly(project(":recordrelay-package-engine"))
    runtimeOnly(project(":recordrelay-adapter-postgres"))
    runtimeOnly(project(":recordrelay-adapter-mongodb"))
    runtimeOnly(project(":recordrelay-adapter-mysql"))
    runtimeOnly(project(":recordrelay-adapter-sqlserver"))
    runtimeOnly(project(":recordrelay-adapter-oracle"))
    runtimeOnly(project(":recordrelay-adapter-sqlite"))
    runtimeOnly(project(":recordrelay-adapter-cassandra"))
    runtimeOnly(project(":recordrelay-adapter-redis"))
    runtimeOnly(project(":recordrelay-adapter-elasticsearch"))
    runtimeOnly(project(":recordrelay-adapter-file"))
    compileOnly(libs.spotbugs.annotations)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit5)
    testRuntimeOnly(libs.logback.classic)
}

// ── Custom plugin repository (self-hosted updates) ──────────────────────────
//
// When a plugin is installed from a ZIP rather than the JetBrains Marketplace,
// IDEA only learns about new versions from a repository descriptor named
// updatePlugins.xml. Host the built ZIP and the generated updatePlugins.xml at a
// stable URL, then add that URL under
//   Settings → Plugins → ⚙ → Manage Plugin Repositories…
// After that, new versions show up as an update (no uninstall/reinstall needed).
//
// Generate with:
//   ./gradlew :recordrelay-plugin:generateUpdatePluginsXml -PpluginRepoBaseUrl=https://host/recordrelay
val pluginRepoBaseUrl: Provider<String> =
    providers.gradleProperty("pluginRepoBaseUrl").orElse("https://REPLACE-ME.example.com/recordrelay")

tasks.register("generateUpdatePluginsXml") {
    group = "distribution"
    description = "Generates updatePlugins.xml describing the current plugin build for a custom repo."
    dependsOn("buildPlugin")

    val outFile = layout.buildDirectory.file("distributions/updatePlugins.xml")
    val version = project.version.toString()
    val baseUrl = pluginRepoBaseUrl.map { it.trimEnd('/') }
    val sinceBuild = pluginSinceBuild
    val untilBuild = pluginUntilBuild

    outputs.file(outFile)
    doLast {
        val zipName = "RecordRelay-$version.zip"
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <plugins>
              <plugin id="io.recordrelay.plugin" url="${baseUrl.get()}/$zipName" version="$version">
                <idea-version since-build="$sinceBuild" until-build="$untilBuild"/>
              </plugin>
            </plugins>
            """.trimIndent() + "\n"
        val file = outFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(xml)
        logger.lifecycle("Wrote ${file.absolutePath} (points at ${baseUrl.get()}/$zipName)")
    }
}

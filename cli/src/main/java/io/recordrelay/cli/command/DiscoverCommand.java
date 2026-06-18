/*
 * Copyright 2026 the RecordRelay authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.recordrelay.cli.command;

import io.recordrelay.cli.ExitCode;
import io.recordrelay.cli.RecordRelayCli;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.cli.engine.DiscoveryEngine;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Discovers databases, tables, or columns for a connection.
 *
 * <p>When only {@code --conn} is supplied, databases are listed. Adding {@code --database} lists
 * tables. Adding {@code --table} lists columns.
 */
@Command(name = "discover", description = "Discover databases, tables, or columns.")
public final class DiscoverCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--conn", "-c"},
      required = true,
      description = "Connection profile name")
  String connName;

  @Option(
      names = {"--database", "-d"},
      description = "Database to inspect")
  String database;

  @Option(
      names = {"--table", "-t"},
      description = "Table to inspect (requires --database)")
  String table;

  private DiscoveryEngine discovery = new DiscoveryEngine();

  /** For testing. */
  void setDiscovery(DiscoveryEngine discovery) {
    this.discovery = discovery;
  }

  @Override
  public Integer call() {
    try {
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var profile = resolver.resolve(connName);

      if (table != null) {
        return listColumns(profile);
      }
      if (database != null) {
        return listTables(profile);
      }
      return listDatabases(profile);
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.CONNECTION_ERROR);
    }
  }

  private int listDatabases(io.recordrelay.core.domain.ConnectionProfile profile) throws Exception {
    var dbs = discovery.discoverDatabases(profile);
    var rows = new ArrayList<List<String>>();
    for (var db : dbs) {
      rows.add(List.of(db.name(), db.type().name()));
    }
    parent.printer().printTable(List.of("DATABASE", "TYPE"), rows);
    return ExitCode.SUCCESS;
  }

  private int listTables(io.recordrelay.core.domain.ConnectionProfile profile) throws Exception {
    var dbRef = new DatabaseRef(database, profile.type());
    var tables = discovery.discoverTables(profile, dbRef);
    var rows = new ArrayList<List<String>>();
    for (TableRef t : tables) {
      String schema = t.schemaName() != null ? t.schemaName() : "";
      rows.add(List.of(schema, t.tableName()));
    }
    parent.printer().printTable(List.of("SCHEMA", "TABLE"), rows);
    return ExitCode.SUCCESS;
  }

  private int listColumns(io.recordrelay.core.domain.ConnectionProfile profile) throws Exception {
    var dbRef = new DatabaseRef(database, profile.type());
    var tableRef = new TableRef(dbRef, null, table);
    var cols = discovery.inspectColumns(profile, tableRef);
    var rows = new ArrayList<List<String>>();
    for (var col : cols) {
      rows.add(
          List.of(
              col.name(),
              col.nativeType(),
              String.valueOf(col.nullable()),
              String.valueOf(col.ordinalPosition())));
    }
    parent.printer().printTable(List.of("COLUMN", "TYPE", "NULLABLE", "POSITION"), rows);
    return ExitCode.SUCCESS;
  }
}

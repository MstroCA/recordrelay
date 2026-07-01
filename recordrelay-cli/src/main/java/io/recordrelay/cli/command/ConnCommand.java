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
import io.recordrelay.cli.config.ConnectionEntry;
import io.recordrelay.cli.engine.ConnProfileResolver;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/** Manages database connection profiles stored in the CLI config. */
@Command(
    name = "conn",
    description = "Manage database connections.",
    subcommands = {
      ConnCommand.AddCommand.class,
      ConnCommand.ListCommand.class,
      ConnCommand.RemoveCommand.class,
      ConnCommand.TestCommand.class
    })
public final class ConnCommand implements Callable<Integer> {

  @ParentCommand RecordRelayCli parent;

  @Override
  public Integer call() {
    new CommandLine(this).usage(System.out);
    return ExitCode.SUCCESS;
  }

  @Command(name = "add", description = "Add a connection profile.")
  static final class AddCommand implements Callable<Integer> {

    @ParentCommand ConnCommand conn;

    @Parameters(index = "0", description = "Connection name")
    String name;

    @Option(names = "--env", description = "Environment name")
    String env = "default";

    @Option(names = "--type", required = true, description = "DB type (e.g. POSTGRESQL)")
    String type;

    @Option(names = "--host", required = true, description = "Database host")
    String host;

    @Option(names = "--port", required = true, description = "Database port")
    int port;

    @Option(names = "--database", required = true, description = "Database name")
    String database;

    @Option(names = "--user", required = true, description = "Database user")
    String user;

    @Option(
        names = "--schema",
        description =
            "Default schema for PostgreSQL (sets currentSchema/search_path so unqualified table"
                + " names resolve). Comma-separated allowed, e.g. myschema,public")
    String schema;

    @Override
    public Integer call() {
      try {
        String plain = readPassword();
        var store = conn.parent.configStore();
        var entry = buildEntry(store.encryptor().encrypt(plain));
        store.addConnection(name, entry);
        conn.parent.printer().printSuccess("Connection '" + name + "' added.");
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(conn.parent, e, ExitCode.CONFIG_ERROR);
      }
    }

    private ConnectionEntry buildEntry(String encryptedPassword) {
      var entry = new ConnectionEntry();
      entry.setEnvironment(env);
      entry.setType(type);
      entry.setHost(host);
      entry.setPort(port);
      entry.setDatabase(database);
      entry.setUser(user);
      entry.setSchema(schema);
      entry.setEncryptedPassword(encryptedPassword);
      return entry;
    }

    private String readPassword() {
      var console = System.console();
      if (console != null) {
        return new String(console.readPassword("Password: "));
      }
      var scanner = new java.util.Scanner(System.in);
      System.out.print("Password: ");
      return scanner.hasNextLine() ? scanner.nextLine() : "";
    }
  }

  @Command(name = "list", description = "List all connection profiles.")
  static final class ListCommand implements Callable<Integer> {

    @ParentCommand ConnCommand conn;

    @Override
    public Integer call() {
      try {
        var config = conn.parent.configStore().load();
        var rows = new ArrayList<List<String>>();
        config
            .getConnections()
            .forEach(
                (n, e) ->
                    rows.add(
                        List.of(
                            n,
                            e.getEnvironment() != null ? e.getEnvironment() : "",
                            e.getType() != null ? e.getType() : "",
                            e.getHost() + ":" + e.getPort(),
                            e.getDatabase() != null ? e.getDatabase() : "",
                            e.getUser() != null ? e.getUser() : "")));
        conn.parent
            .printer()
            .printTable(List.of("NAME", "ENV", "TYPE", "HOST:PORT", "DATABASE", "USER"), rows);
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(conn.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  @Command(name = "remove", description = "Remove a connection profile.")
  static final class RemoveCommand implements Callable<Integer> {

    @ParentCommand ConnCommand conn;

    @Parameters(index = "0", description = "Connection name")
    String name;

    @Override
    public Integer call() {
      try {
        boolean removed = conn.parent.configStore().removeConnection(name);
        if (removed) {
          conn.parent.printer().printSuccess("Connection '" + name + "' removed.");
        } else {
          conn.parent.printer().printError("Connection '" + name + "' not found.");
        }
        return removed ? ExitCode.SUCCESS : ExitCode.CONFIG_ERROR;
      } catch (Exception e) {
        return EnvCommand.handleError(conn.parent, e, ExitCode.CONFIG_ERROR);
      }
    }
  }

  @Command(name = "test", description = "Test connectivity for a connection profile.")
  static final class TestCommand implements Callable<Integer> {

    @ParentCommand ConnCommand conn;

    @Parameters(index = "0", description = "Connection name")
    String name;

    @Override
    public Integer call() {
      try {
        var store = conn.parent.configStore();
        var resolver = new ConnProfileResolver(store);
        var profile = resolver.resolve(name);
        var connector = ConnectorRegistry.findConnector(profile);
        connector.testConnection(profile);
        conn.parent.printer().printSuccess("Connection '" + name + "' is reachable.");
        return ExitCode.SUCCESS;
      } catch (Exception e) {
        return EnvCommand.handleError(conn.parent, e, ExitCode.CONNECTION_ERROR);
      }
    }
  }
}

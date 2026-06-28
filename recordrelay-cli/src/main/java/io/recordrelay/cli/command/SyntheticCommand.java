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
import io.recordrelay.core.spi.ConnectorRegistry;
import io.recordrelay.synthetic.CsvExporter;
import io.recordrelay.synthetic.SyntheticDataGenerator;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Generates realistic synthetic data for a table and writes it to a target connection or CSV.
 *
 * <pre>
 * rr synth --from prod --database mydb --table customers --rows 500 --target local
 * rr synth --from prod --database mydb --table orders   --rows 1000 --output orders.csv
 * </pre>
 */
@Command(name = "synth", description = "Generate realistic synthetic data from a table schema.")
public final class SyntheticCommand implements Callable<Integer> {

  @ParentCommand private RecordRelayCli parent;

  @Option(
      names = {"--from", "-f"},
      required = true,
      description = "Source connection profile (schema is read from here)")
  String fromConn;

  @Option(
      names = {"--database", "-d"},
      required = true,
      description = "Database name")
  String database;

  @Option(
      names = {"--table", "-t"},
      required = true,
      description = "Table to generate synthetic rows for")
  String table;

  @Option(
      names = {"--rows", "-n"},
      defaultValue = "100",
      description = "Number of rows to generate (default: 100)")
  int rows;

  @Option(
      names = {"--target"},
      description = "Target connection to insert rows into (mutually exclusive with --output)")
  String targetConn;

  @Option(
      names = {"--output", "-o"},
      description = "CSV output file path (mutually exclusive with --target)")
  Path outputFile;

  @Option(
      names = {"--seed"},
      description = "Random seed for reproducible generation (default: random)")
  Long seed;

  @Option(
      names = {"--start-id"},
      defaultValue = "1",
      description = "Starting value for PK auto-increment columns (default: 1)")
  int startId;

  @Override
  public Integer call() {
    if (targetConn != null && outputFile != null) {
      System.err.println("Specify --target OR --output, not both.");
      return ExitCode.VALIDATION_ERROR;
    }
    if (targetConn == null && outputFile == null) {
      System.err.println("Specify --target (insert into DB) or --output (write CSV).");
      return ExitCode.VALIDATION_ERROR;
    }

    try {
      var store = parent.configStore();
      var resolver = new ConnProfileResolver(store);
      var srcProfile = resolver.resolve(fromConn);
      var discovery = new DiscoveryEngine();
      var dbRef = new DatabaseRef(database, srcProfile.type());
      var tableRef = new TableRef(dbRef, null, table);

      var columns = discovery.inspectColumns(srcProfile, tableRef);
      if (columns.isEmpty()) {
        System.err.println("No columns found for table: " + table);
        return ExitCode.VALIDATION_ERROR;
      }

      var gen = new SyntheticDataGenerator(columns).startId(startId);
      if (seed != null) gen = gen.seed(seed);
      var records = gen.generate(rows);

      if (outputFile != null) {
        CsvExporter.write(records, outputFile);
        System.out.printf("Wrote %d rows to %s%n", records.size(), outputFile);
      } else {
        var tgtProfile = resolver.resolve(targetConn);
        var tgtConnector = ConnectorRegistry.findConnector(tgtProfile);
        var writer = tgtConnector.createWriter();
        writer.open(tgtProfile, tableRef, true);
        int written = 0;
        for (var rec : records) {
          writer.write(rec);
          written++;
        }
        writer.flush();
        writer.close();
        System.out.printf("Inserted %d synthetic rows into %s.%s%n", written, database, table);
      }
      return ExitCode.SUCCESS;
    } catch (Exception e) {
      return EnvCommand.handleError(parent, e, ExitCode.TRANSFER_FAILED);
    }
  }
}

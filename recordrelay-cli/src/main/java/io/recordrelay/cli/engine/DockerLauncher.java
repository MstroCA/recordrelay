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
package io.recordrelay.cli.engine;

import io.recordrelay.core.domain.DatabaseType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Launches a Docker container for a given database type and waits until it accepts connections.
 *
 * <p>Requires Docker CLI ({@code docker}) to be on the PATH.
 */
public final class DockerLauncher {

  /** Result of a successful container launch. */
  public record ContainerInfo(
      String containerId, String host, int hostPort, String connectionString, DatabaseType type) {

    public String shortId() {
      return containerId.length() > 12 ? containerId.substring(0, 12) : containerId;
    }
  }

  private DockerLauncher() {}

  /**
   * Starts a Docker container for {@code type} and returns the connection details.
   *
   * @param type the database engine to start
   * @param database the logical database / schema name to create inside the container
   */
  public static ContainerInfo launch(DatabaseType type, String database) throws Exception {
    var spec = specFor(type, database);

    var cmd = new ArrayList<String>();
    cmd.add("docker");
    cmd.add("run");
    cmd.add("-d");
    cmd.add("--rm");
    cmd.add("-p");
    cmd.add("0:" + spec.containerPort());
    cmd.addAll(spec.envFlags());
    cmd.add(spec.image());

    var containerId = exec(cmd).strip();
    if (containerId.isEmpty()) {
      throw new RuntimeException("docker run produced no container ID — is Docker running?");
    }

    waitUntilReady(containerId, spec.readyLogSnippet(), spec.startupSeconds());

    int hostPort = mappedPort(containerId, spec.containerPort());
    var connStr = spec.connectionString("localhost", hostPort, database);

    return new ContainerInfo(containerId, "localhost", hostPort, connStr, type);
  }

  // ── Docker-specific configuration per database type ───────────────────────

  private record DbSpec(
      String image,
      int containerPort,
      List<String> envFlags,
      String readyLogSnippet,
      int startupSeconds,
      java.util.function.BiFunction<String, String, String> jdbcTemplate) {

    String connectionString(String host, int port, String db) {
      return jdbcTemplate.apply(host + ":" + port, db);
    }
  }

  private static DbSpec specFor(DatabaseType type, String database) {
    return switch (type) {
      case POSTGRESQL ->
          new DbSpec(
              "postgres:16-alpine",
              5432,
              List.of(
                  "-e", "POSTGRES_DB=" + database,
                  "-e", "POSTGRES_USER=rr_test",
                  "-e", "POSTGRES_PASSWORD=rr_test"),
              "database system is ready to accept connections",
              15,
              (hp, db) -> "jdbc:postgresql://" + hp + "/" + db + "?user=rr_test&password=rr_test");
      case MYSQL ->
          new DbSpec(
              "mysql:8",
              3306,
              List.of(
                  "-e", "MYSQL_DATABASE=" + database,
                  "-e", "MYSQL_ROOT_PASSWORD=rr_test",
                  "-e", "MYSQL_USER=rr_test",
                  "-e", "MYSQL_PASSWORD=rr_test"),
              "port: 3306  MySQL Community Server",
              30,
              (hp, db) -> "jdbc:mysql://" + hp + "/" + db + "?user=rr_test&password=rr_test");
      case MARIADB ->
          new DbSpec(
              "mariadb:11",
              3306,
              List.of(
                  "-e", "MARIADB_DATABASE=" + database,
                  "-e", "MARIADB_ROOT_PASSWORD=rr_test",
                  "-e", "MARIADB_USER=rr_test",
                  "-e", "MARIADB_PASSWORD=rr_test"),
              "mariadbd: ready for connections",
              25,
              (hp, db) -> "jdbc:mariadb://" + hp + "/" + db + "?user=rr_test&password=rr_test");
      case SQLSERVER ->
          new DbSpec(
              "mcr.microsoft.com/mssql/server:2022-latest",
              1433,
              List.of(
                  "-e", "ACCEPT_EULA=Y",
                  "-e", "MSSQL_SA_PASSWORD=Rr_Test1234!"),
              "SQL Server is now ready for client connections",
              45,
              (hp, db) ->
                  "jdbc:sqlserver://"
                      + hp
                      + ";databaseName="
                      + db
                      + ";user=sa;password=Rr_Test1234!");
      case MONGODB ->
          new DbSpec(
              "mongo:7",
              27017,
              List.of(),
              "Waiting for connections",
              10,
              (hp, db) -> "mongodb://" + hp + "/" + db);
      default ->
          throw new IllegalArgumentException(
              "No Docker image configured for database type: " + type);
    };
  }

  // ── Process helpers ───────────────────────────────────────────────────────

  private static void waitUntilReady(String containerId, String readySnippet, int maxSeconds)
      throws Exception {
    long deadline = System.currentTimeMillis() + (long) maxSeconds * 1000;
    while (System.currentTimeMillis() < deadline) {
      Thread.sleep(2_000);
      var logs = exec(List.of("docker", "logs", "--tail", "20", containerId));
      if (logs.contains(readySnippet)) {
        return;
      }
    }
    throw new RuntimeException(
        "Container "
            + containerId.substring(0, 12)
            + " did not become ready within "
            + maxSeconds
            + "s — check 'docker logs "
            + containerId.substring(0, 12)
            + "'");
  }

  private static int mappedPort(String containerId, int containerPort) throws Exception {
    var out =
        exec(
            List.of(
                "docker",
                "inspect",
                "--format",
                "{{(index (index .NetworkSettings.Ports \""
                    + containerPort
                    + "/tcp\") 0).HostPort}}",
                containerId));
    try {
      return Integer.parseInt(out.strip());
    } catch (NumberFormatException e) {
      throw new RuntimeException("Could not determine mapped port for " + containerId + ": " + out);
    }
  }

  private static String exec(List<String> cmd) throws Exception {
    var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
    var process = pb.start();
    var sb = new StringBuilder();
    try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!sb.isEmpty()) sb.append('\n');
        sb.append(line);
      }
    }
    process.waitFor(60, TimeUnit.SECONDS);
    return sb.toString();
  }
}

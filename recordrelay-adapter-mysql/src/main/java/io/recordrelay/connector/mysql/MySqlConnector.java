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
package io.recordrelay.connector.mysql;

import io.recordrelay.connector.jdbc.AbstractJdbcConnector;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseType;
import io.recordrelay.core.port.out.RecordReader;
import io.recordrelay.core.port.out.RecordWriter;
import io.recordrelay.core.port.out.SchemaInspector;

/**
 * {@link io.recordrelay.core.port.out.ContextProviderPort} adapter for MySQL and MariaDB.
 *
 * <p>Registered via {@code META-INF/services} for ServiceLoader discovery. Supports {@link
 * DatabaseType#MYSQL} and {@link DatabaseType#MARIADB} profiles.
 */
public final class MySqlConnector extends AbstractJdbcConnector {

  @Override
  public String connectorId() {
    return "mysql";
  }

  @Override
  public boolean supports(ConnectionProfile profile) {
    return profile.type() == DatabaseType.MYSQL || profile.type() == DatabaseType.MARIADB;
  }

  @Override
  protected String jdbcScheme() {
    return "mysql";
  }

  @Override
  protected String listDatabasesSql() {
    return "SHOW DATABASES";
  }

  @Override
  public SchemaInspector schemaInspector() {
    return new MySqlSchemaInspector();
  }

  @Override
  public RecordReader createReader() {
    return new MySqlRecordReader();
  }

  @Override
  public RecordWriter createWriter() {
    return new MySqlRecordWriter();
  }
}

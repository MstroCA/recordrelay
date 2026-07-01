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
package io.recordrelay.cli.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A companion (satellite) table definition persisted per entity in {@code
 * ~/.recordrelay/config.json} under the {@code satellites} map.
 *
 * <p>Declares a table in a <em>separate</em> database that is linked to the cloned root entity by a
 * single column. After a clone, RecordRelay copies the matching rows from {@code sourceConn} into
 * {@code targetConn}, remapping {@code linkColumn} to the newly allocated root id.
 *
 * <pre>{@code
 * "satellites": {
 *   "beyanname": [
 *     { "sourceConn": "prod-user", "targetConn": "test-user",
 *       "table": "read_model", "linkColumn": "beyanname_id" }
 *   ]
 * }
 * }</pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SatelliteEntry {

  private String sourceConn;
  private String targetConn;
  private String table;
  private String linkColumn;

  /** Optional; when null the engine auto-detects the primary-key column. */
  private String pkColumn;

  public SatelliteEntry() {}

  public String getSourceConn() {
    return sourceConn;
  }

  public void setSourceConn(String sourceConn) {
    this.sourceConn = sourceConn;
  }

  public String getTargetConn() {
    return targetConn;
  }

  public void setTargetConn(String targetConn) {
    this.targetConn = targetConn;
  }

  public String getTable() {
    return table;
  }

  public void setTable(String table) {
    this.table = table;
  }

  public String getLinkColumn() {
    return linkColumn;
  }

  public void setLinkColumn(String linkColumn) {
    this.linkColumn = linkColumn;
  }

  public String getPkColumn() {
    return pkColumn;
  }

  public void setPkColumn(String pkColumn) {
    this.pkColumn = pkColumn;
  }
}

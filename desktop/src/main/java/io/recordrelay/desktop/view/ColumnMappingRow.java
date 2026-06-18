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
package io.recordrelay.desktop.view;

import io.recordrelay.core.domain.ColumnMapping;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Observable row in the column-mapping TableView on the Transfer screen. */
public final class ColumnMappingRow {

  private final BooleanProperty enabled = new SimpleBooleanProperty(true);
  private final StringProperty sourceColumn;
  private final StringProperty sourceType;
  private final StringProperty targetColumn;
  private final StringProperty transform;
  private final StringProperty overrideValue;

  public ColumnMappingRow(String sourceCol, String srcType) {
    this.sourceColumn = new SimpleStringProperty(sourceCol);
    this.sourceType = new SimpleStringProperty(srcType == null ? "" : srcType);
    this.targetColumn = new SimpleStringProperty(sourceCol);
    this.transform = new SimpleStringProperty("");
    this.overrideValue = new SimpleStringProperty("");
  }

  /** Returns the enabled flag property (unchecked = excluded from transfer). */
  public BooleanProperty enabledProperty() {
    return enabled;
  }

  /** Returns the source column name property (read-only in the UI). */
  public StringProperty sourceColumnProperty() {
    return sourceColumn;
  }

  /** Returns the native source type property (read-only in the UI). */
  public StringProperty sourceTypeProperty() {
    return sourceType;
  }

  /** Returns the target column name property (editable — defaults to source column name). */
  public StringProperty targetColumnProperty() {
    return targetColumn;
  }

  /** Returns the transform spec property (e.g. {@code upper}, {@code cast:int}). */
  public StringProperty transformProperty() {
    return transform;
  }

  /**
   * Returns the override-value property; if non-blank, the literal is written instead of source.
   */
  public StringProperty overrideValueProperty() {
    return overrideValue;
  }

  /** Returns {@code true} when this column is included in the transfer. */
  public boolean isEnabled() {
    return enabled.get();
  }

  /**
   * Converts this row to a {@link ColumnMapping} for use in a {@link
   * io.recordrelay.core.domain.MappingDefinition}.
   *
   * <p>If {@code overrideValue} is set, the transform becomes {@code "const:<value>"}. If only
   * {@code transform} is set, it is passed through as-is. Returns {@code null} when the row is
   * disabled (callers should filter with {@link #isEnabled()}).
   */
  public ColumnMapping toColumnMapping() {
    String src = sourceColumn.get();
    String tgt = targetColumn.get();
    if (tgt == null || tgt.isBlank()) {
      tgt = src;
    }
    String ovr = overrideValue.get();
    String tfm = transform.get();
    if (ovr != null && !ovr.isBlank()) {
      tfm = "const:" + ovr;
    } else if (tfm == null || tfm.isBlank()) {
      tfm = null;
    }
    return new ColumnMapping(src, tgt, tfm);
  }
}

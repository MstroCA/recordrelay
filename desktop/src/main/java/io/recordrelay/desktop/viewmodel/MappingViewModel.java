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
package io.recordrelay.desktop.viewmodel;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/** ViewModel for the column mapping UI — tracks pairs, schema-match %, and format preview. */
public final class MappingViewModel extends BaseViewModel {

  private final ObservableList<ColumnPair> columnPairs = FXCollections.observableArrayList();
  private final DoubleProperty matchPercent = new SimpleDoubleProperty(0);
  private final StringProperty formatPreview = new SimpleStringProperty("");
  private final StringProperty selectedFormat = new SimpleStringProperty("json");
  private final StringProperty mappingId = new SimpleStringProperty("");

  /** Returns the observable list of source→target column pairs. */
  public ObservableList<ColumnPair> columnPairsProperty() {
    return columnPairs;
  }

  /** Returns the schema-match percentage property (0.0–100.0). */
  public DoubleProperty matchPercentProperty() {
    return matchPercent;
  }

  /** Returns the formatted mapping preview text property. */
  public StringProperty formatPreviewProperty() {
    return formatPreview;
  }

  /** Returns the selected output format property (e.g., "json", "yaml"). */
  public StringProperty selectedFormatProperty() {
    return selectedFormat;
  }

  /** Returns the mapping identifier property used to persist the mapping. */
  public StringProperty mappingIdProperty() {
    return mappingId;
  }

  /** Updates the schema-match percentage (0–100). */
  public void setMatchPercent(double percent) {
    matchPercent.set(percent);
  }

  /** Updates the formatted preview text for the current {@code selectedFormat}. */
  public void setPreview(String text) {
    formatPreview.set(text);
  }

  /** A single source→target column mapping pair displayed in the mapping table. */
  public static final class ColumnPair {

    private final StringProperty sourceColumn = new SimpleStringProperty("");
    private final StringProperty targetColumn = new SimpleStringProperty("");
    private final StringProperty transform = new SimpleStringProperty("");
    private final StringProperty warning = new SimpleStringProperty("");

    public ColumnPair(String src, String tgt, String xform) {
      sourceColumn.set(src);
      targetColumn.set(tgt);
      transform.set(xform != null ? xform : "");
    }

    /** Returns the source column name property. */
    public StringProperty sourceColumnProperty() {
      return sourceColumn;
    }

    /** Returns the target column name property. */
    public StringProperty targetColumnProperty() {
      return targetColumn;
    }

    /** Returns the optional transform expression property. */
    public StringProperty transformProperty() {
      return transform;
    }

    /** Returns the validation warning property (empty when no issue). */
    public StringProperty warningProperty() {
      return warning;
    }
  }
}

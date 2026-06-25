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

import io.recordrelay.cli.flow.QueryFlowModel;
import io.recordrelay.cli.flow.QueryFlowModel.NodeEntry;
import io.recordrelay.cli.flow.QueryFlowModel.OrderByEntry;
import io.recordrelay.cli.flow.QueryFlowModel.WhereFilter;
import java.util.ArrayList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Right-side conditions panel for the desktop flow query builder.
 *
 * <p>Displays WHERE filters, ORDER BY clauses, and a LIMIT spinner. Rebuilds
 * whenever the model changes. Each control is wired to mutate the model directly.
 */
public final class FlowConditionsPane extends VBox {

  private static final String[] OPERATORS = {
    "=", "!=", "<", ">", "<=", ">=", "LIKE", "IS NULL", "IS NOT NULL"
  };

  private final QueryFlowModel model;

  /** Creates the conditions pane bound to the given model. */
  public FlowConditionsPane(QueryFlowModel model) {
    this.model = model;
    setPadding(new Insets(8));
    setSpacing(4);
    setStyle("-fx-background-color: #F0F4FA;");
    setPrefWidth(210);
    setMinWidth(180);
    model.addChangeListener(this::rebuild);
    rebuild();
  }

  // ── Rebuild ────────────────────────────────────────────────────────────────────

  private void rebuild() {
    getChildren().clear();
    getChildren().add(sectionLabel("WHERE"));
    for (int i = 0; i < model.filters().size(); i++) {
      getChildren().add(buildFilterRow(model.filters().get(i), i));
    }
    getChildren().add(addBtn("+ Filter", this::addEmptyFilter));
    getChildren().add(new Separator());

    getChildren().add(sectionLabel("ORDER BY"));
    for (int i = 0; i < model.orderBys().size(); i++) {
      getChildren().add(buildOrderByRow(model.orderBys().get(i), i));
    }
    getChildren().add(addBtn("+ Order", this::addEmptyOrderBy));
    getChildren().add(new Separator());

    getChildren().add(sectionLabel("LIMIT"));
    getChildren().add(buildLimitRow());
  }

  // ── Section helpers ─────────────────────────────────────────────────────────────

  private static Label sectionLabel(String text) {
    var lbl = new Label(text);
    lbl.setFont(Font.font("System", FontWeight.BOLD, 10));
    lbl.setTextFill(Color.web("#1565C0"));
    VBox.setMargin(lbl, new Insets(4, 0, 2, 0));
    return lbl;
  }

  private static Button addBtn(String text, Runnable action) {
    var btn = new Button(text);
    btn.setFont(Font.font("System", 10));
    btn.setTextFill(Color.web("#42A5F5"));
    btn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
    btn.setMaxWidth(Double.MAX_VALUE);
    btn.setOnAction(e -> action.run());
    return btn;
  }

  private static Button removeBtn(Runnable action) {
    var btn = new Button("×");
    btn.setFont(Font.font("System", FontWeight.BOLD, 10));
    btn.setTextFill(Color.RED);
    btn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
    btn.setOnAction(e -> action.run());
    return btn;
  }

  // ── Filter row ──────────────────────────────────────────────────────────────────

  private VBox buildFilterRow(WhereFilter filter, int index) {
    var colCombo = buildColCombo(filter.tableAlias() + "." + filter.column());
    var opCombo = new ComboBox<String>();
    opCombo.getItems().addAll(OPERATORS);
    opCombo.setValue(filter.operator());
    opCombo.setMaxWidth(Double.MAX_VALUE);

    var valField = new TextField(filter.value());
    valField.setVisible(!filter.operator().startsWith("IS"));
    valField.setManaged(!filter.operator().startsWith("IS"));
    HBox.setHgrow(valField, Priority.ALWAYS);

    opCombo.setOnAction(e -> {
      String op = opCombo.getValue();
      boolean isNull = op != null && op.startsWith("IS");
      valField.setVisible(!isNull);
      valField.setManaged(!isNull);
      updateFilter(index, colCombo, opCombo, valField);
    });
    colCombo.setOnAction(e -> updateFilter(index, colCombo, opCombo, valField));
    valField.textProperty().addListener((obs, old, val) ->
        updateFilter(index, colCombo, opCombo, valField));

    var row1 = new HBox(4, colCombo, removeBtn(() -> model.removeFilter(index)));
    var row2 = new HBox(4, opCombo, valField);
    HBox.setHgrow(colCombo, Priority.ALWAYS);
    HBox.setHgrow(opCombo, Priority.ALWAYS);

    var panel = new VBox(2, row1, row2);
    panel.setStyle("-fx-background-color: white; -fx-border-color: #D0D7E2;"
        + " -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 4;");
    return panel;
  }

  private void updateFilter(int index, ComboBox<String> colCombo,
      ComboBox<String> opCombo, TextField valField) {
    String colFull = colCombo.getValue();
    String op = opCombo.getValue();
    if (colFull == null || op == null || index >= model.filters().size()) {
      return;
    }
    String[] parts = colFull.split("\\.", 2);
    if (parts.length < 2) {
      return;
    }
    model.removeFilter(index);
    model.addFilter(parts[0], parts[1], op, valField.getText());
  }

  // ── ORDER BY row ────────────────────────────────────────────────────────────────

  private HBox buildOrderByRow(OrderByEntry ob, int index) {
    var colCombo = buildColCombo(ob.tableAlias() + "." + ob.column());
    var dirCombo = new ComboBox<String>();
    dirCombo.getItems().addAll("ASC", "DESC");
    dirCombo.setValue(ob.ascending() ? "ASC" : "DESC");

    Runnable update = () -> {
      String colFull = colCombo.getValue();
      String dir = dirCombo.getValue();
      if (colFull == null || dir == null) {
        return;
      }
      String[] parts = colFull.split("\\.", 2);
      if (parts.length >= 2) {
        model.removeOrderBy(index);
        model.addOrderBy(parts[0], parts[1], "ASC".equals(dir));
      }
    };
    colCombo.setOnAction(e -> update.run());
    dirCombo.setOnAction(e -> update.run());

    var row = new HBox(4, colCombo, dirCombo, removeBtn(() -> model.removeOrderBy(index)));
    HBox.setHgrow(colCombo, Priority.ALWAYS);
    row.setStyle("-fx-background-color: white; -fx-border-color: #D0D7E2;"
        + " -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 4;");
    return row;
  }

  // ── LIMIT row ───────────────────────────────────────────────────────────────────

  private HBox buildLimitRow() {
    var factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100000, model.getLimit(), 10);
    var spinner = new Spinner<Integer>(factory);
    spinner.setEditable(true);
    spinner.setPrefWidth(90);
    spinner.valueProperty().addListener((obs, old, val) -> {
      if (val != null) {
        model.setLimit(val);
      }
    });
    return new HBox(6, new Label("rows:"), spinner);
  }

  // ── Column combo ─────────────────────────────────────────────────────────────────

  private ComboBox<String> buildColCombo(String selected) {
    var cols = new ArrayList<String>();
    for (NodeEntry node : model.nodes()) {
      for (var col : node.columns()) {
        cols.add(node.alias() + "." + col.name());
      }
    }
    var combo = new ComboBox<String>();
    combo.getItems().addAll(cols);
    combo.setValue(selected);
    combo.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(combo, Priority.ALWAYS);
    return combo;
  }

  // ── Empty-add helpers ─────────────────────────────────────────────────────────────

  private void addEmptyFilter() {
    if (model.nodes().isEmpty()) {
      return;
    }
    var first = model.nodes().get(0);
    String col = first.columns().isEmpty() ? "" : first.columns().get(0).name();
    model.addFilter(first.alias(), col, "=", "");
  }

  private void addEmptyOrderBy() {
    if (model.nodes().isEmpty()) {
      return;
    }
    var first = model.nodes().get(0);
    String col = first.columns().isEmpty() ? "" : first.columns().get(0).name();
    model.addOrderBy(first.alias(), col, true);
  }
}

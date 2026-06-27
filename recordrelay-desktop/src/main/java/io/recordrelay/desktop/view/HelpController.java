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

import io.recordrelay.core.i18n.Messages;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Controller for the in-app Help & Documentation screen. */
public final class HelpController implements Refreshable {

  private static final List<String> TOPIC_IDS =
      List.of(
          "getting-started",
          "clone-context",
          "conflict-resolution",
          "environments",
          "connections",
          "discovery",
          "monitor",
          "graph-view",
          "migration-drift",
          "row-count-diff",
          "query-analyzer",
          "connection-health",
          "masking-coverage",
          "presets",
          "scheduled-sync",
          "faq");

  @FXML private ListView<String> topicList;
  @FXML private VBox contentBox;
  @FXML private TextField tfSearch;
  @FXML private Label lblEmpty;

  private final ObservableList<String> allTopics = FXCollections.observableArrayList();
  private FilteredList<String> filteredTopics;

  @FXML
  void initialize() {
    for (String id : TOPIC_IDS) {
      allTopics.add(id);
    }
    filteredTopics = new FilteredList<>(allTopics, id -> true);
    topicList.setItems(filteredTopics);

    topicList.setCellFactory(
        lv ->
            new javafx.scene.control.ListCell<>() {
              @Override
              protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty);
                setText(empty || id == null ? null : Messages.get("help.topic." + id + ".title"));
              }
            });

    tfSearch
        .textProperty()
        .addListener(
            (obs, old, text) -> {
              String lower = text == null ? "" : text.toLowerCase();
              filteredTopics.setPredicate(
                  id -> {
                    if (lower.isBlank()) {
                      return true;
                    }
                    String title = Messages.get("help.topic." + id + ".title").toLowerCase();
                    return title.contains(lower) || id.contains(lower);
                  });
            });

    topicList
        .getSelectionModel()
        .selectedItemProperty()
        .addListener((obs, old, selected) -> showTopic(selected));
  }

  @Override
  public void refresh() {
    // Re-render selected topic so content reflects any language change
    topicList.refresh();
    String selected = topicList.getSelectionModel().getSelectedItem();
    if (selected != null) {
      showTopic(selected);
    }
  }

  private void showTopic(String topicId) {
    contentBox.getChildren().clear();
    if (topicId == null) {
      contentBox.getChildren().add(lblEmpty);
      return;
    }
    String body = Messages.get("help.topic." + topicId + ".body");
    for (Node node : parseBody(body)) {
      contentBox.getChildren().add(node);
    }
  }

  /**
   * Parses a simple markup format into JavaFX nodes.
   *
   * <p>Lines starting with {@code # } become headings, {@code ## } become subheadings, {@code •}
   * become indented bullets. Empty lines become vertical spacers.
   */
  private static List<Node> parseBody(String body) {
    var nodes = new java.util.ArrayList<Node>();
    String[] lines = body.split("\n", -1);
    for (String raw : lines) {
      String line = raw.stripTrailing();
      if (line.startsWith("# ")) {
        nodes.add(styledLabel(line.substring(2), "help-heading"));
        nodes.add(spacer(6));
      } else if (line.startsWith("## ")) {
        nodes.add(spacer(10));
        nodes.add(styledLabel(line.substring(3), "help-subheading"));
        nodes.add(spacer(4));
      } else if (line.startsWith("• ")) {
        Label lbl = styledLabel("• " + line.substring(2), "help-bullet");
        lbl.setPadding(new javafx.geometry.Insets(0, 0, 0, 16));
        nodes.add(lbl);
      } else if (line.isBlank()) {
        nodes.add(spacer(8));
      } else {
        nodes.add(styledLabel(line, "help-body"));
      }
    }
    return nodes;
  }

  private static Label styledLabel(String text, String styleClass) {
    Label lbl = new Label(text);
    lbl.getStyleClass().add(styleClass);
    lbl.setWrapText(true);
    lbl.setMaxWidth(Double.MAX_VALUE);
    return lbl;
  }

  private static Region spacer(double height) {
    Region r = new Region();
    r.setPrefHeight(height);
    r.setMinHeight(height);
    r.setMaxHeight(height);
    return r;
  }
}

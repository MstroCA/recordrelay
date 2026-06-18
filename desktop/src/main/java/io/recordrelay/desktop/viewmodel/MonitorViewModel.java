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

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.chart.XYChart;

/** ViewModel for the Dashboard/Monitor screen. Holds live metrics for binding to charts. */
public final class MonitorViewModel extends BaseViewModel {

  private static final int MAX_CHART_POINTS = 60;

  private final LongProperty transferredCount = new SimpleLongProperty(0);
  private final LongProperty failedCount = new SimpleLongProperty(0);
  private final LongProperty throughputNow = new SimpleLongProperty(0);
  private final StringProperty latencyP50 = new SimpleStringProperty("—");
  private final StringProperty latencyP95 = new SimpleStringProperty("—");
  private final StringProperty latencyP99 = new SimpleStringProperty("—");
  private final StringProperty duration = new SimpleStringProperty("—");

  private final XYChart.Series<Number, Number> throughputSeries = new XYChart.Series<>();
  private final ObservableList<HealthEntry> healthIndicators = FXCollections.observableArrayList();

  private int chartTick = 0;

  public MonitorViewModel() {
    throughputSeries.setName("rec/s");
  }

  /** Returns the total transferred record count property. */
  public LongProperty transferredCountProperty() {
    return transferredCount;
  }

  /** Returns the total failed record count property. */
  public LongProperty failedCountProperty() {
    return failedCount;
  }

  /** Returns the current throughput in records/second property. */
  public LongProperty throughputNowProperty() {
    return throughputNow;
  }

  /** Returns the p50 latency label property. */
  public StringProperty latencyP50Property() {
    return latencyP50;
  }

  /** Returns the p95 latency label property. */
  public StringProperty latencyP95Property() {
    return latencyP95;
  }

  /** Returns the p99 latency label property. */
  public StringProperty latencyP99Property() {
    return latencyP99;
  }

  /** Returns the human-readable duration label property. */
  public StringProperty durationProperty() {
    return duration;
  }

  /** Returns the chart data series for throughput over time. */
  public XYChart.Series<Number, Number> throughputSeries() {
    return throughputSeries;
  }

  /** Returns the observable list of DB health entries. */
  public ObservableList<HealthEntry> healthIndicators() {
    return healthIndicators;
  }

  /** Appends a new throughput sample to the chart series (call from JavaFX thread). */
  public void recordThroughput(long recs) {
    throughputNow.set(recs);
    throughputSeries.getData().add(new XYChart.Data<>(chartTick++, recs));
    if (throughputSeries.getData().size() > MAX_CHART_POINTS) {
      throughputSeries.getData().remove(0);
    }
  }

  /** Updates aggregate counters (call from JavaFX thread). */
  public void updateCounters(long done, long failed, String durationStr) {
    transferredCount.set(done);
    failedCount.set(failed);
    duration.set(durationStr);
  }

  /** Updates latency percentile labels (call from JavaFX thread). */
  public void updateLatency(long p50ms, long p95ms, long p99ms) {
    latencyP50.set(p50ms + " ms");
    latencyP95.set(p95ms + " ms");
    latencyP99.set(p99ms + " ms");
  }

  /** Resets all counters, clears the throughput chart, and removes health entries. */
  public void reset() {
    transferredCount.set(0);
    failedCount.set(0);
    throughputNow.set(0);
    duration.set("—");
    latencyP50.set("—");
    latencyP95.set("—");
    latencyP99.set("—");
    throughputSeries.getData().clear();
    chartTick = 0;
    healthIndicators.clear();
  }

  /** A single DB health indicator row. */
  public record HealthEntry(String name, boolean reachable, String detail) {}
}

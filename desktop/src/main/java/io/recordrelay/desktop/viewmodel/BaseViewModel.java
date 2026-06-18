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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/** Base class for all ViewModels. Provides shared {@code busy} and {@code error} state. */
public abstract class BaseViewModel {

  private final BooleanProperty busy = new SimpleBooleanProperty(false);
  private final StringProperty error = new SimpleStringProperty("");

  /** Returns {@code true} while a background operation is in progress. */
  public ReadOnlyBooleanProperty busyProperty() {
    return busy;
  }

  /** Returns the last error message, or empty string when no error. */
  public ReadOnlyStringProperty errorProperty() {
    return error;
  }

  protected void setBusy(boolean value) {
    busy.set(value);
  }

  protected void setError(String message) {
    error.set(message != null ? message : "");
  }

  protected void clearError() {
    error.set("");
  }
}

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
package io.recordrelay.core.exception;

/**
 * Unchecked exception thrown at connector boundaries to wrap DB driver checked exceptions.
 *
 * <p>All {@link io.recordrelay.core.port.out.DataSourceConnector} and {@link
 * io.recordrelay.core.port.out.SchemaInspector} implementations must catch driver-specific checked
 * exceptions and re-throw them as {@code ConnectorException} so callers do not need to be aware of
 * driver-specific exception hierarchies.
 */
public final class ConnectorException extends RuntimeException {

  /**
   * Creates an exception with a descriptive message and no cause.
   *
   * @param message description of what failed
   */
  public ConnectorException(String message) {
    super(message);
  }

  /**
   * Creates an exception wrapping a lower-level driver exception.
   *
   * @param message description of what failed
   * @param cause the underlying driver exception
   */
  public ConnectorException(String message, Throwable cause) {
    super(message, cause);
  }
}

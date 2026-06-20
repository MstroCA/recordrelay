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
package io.recordrelay.core.domain;

import java.util.Objects;

/**
 * Username and password pair for a database connection.
 *
 * <p>Passwords are stored as plain {@link String} in Phase 1. Phase 3 will replace this with
 * encrypted-at-rest storage backed by the OS keychain.
 */
public record Credentials(String username, String password) {

  /** Validates that username is non-null. Password may be empty for passwordless connections. */
  public Credentials {
    Objects.requireNonNull(username, "username");
    password = password == null ? "" : password;
  }

  /**
   * Returns a credentials instance with no password (e.g., for local dev DBs).
   *
   * @param username the database user
   * @return credentials with an empty password
   */
  public static Credentials of(String username) {
    return new Credentials(username, "");
  }
}

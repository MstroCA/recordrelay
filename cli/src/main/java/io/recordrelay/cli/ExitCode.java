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
package io.recordrelay.cli;

/** Process exit codes returned by all CLI commands. */
public final class ExitCode {

  public static final int SUCCESS = 0;
  public static final int TRANSFER_FAILED = 1;
  public static final int VALIDATION_ERROR = 2;
  public static final int CONNECTION_ERROR = 3;
  public static final int CONFIG_ERROR = 4;

  private ExitCode() {}
}

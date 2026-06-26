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
package io.recordrelay.cli.engine;

import io.recordrelay.core.clone.domain.MaskingConfig;
import io.recordrelay.core.domain.ColumnMeta;
import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.domain.DatabaseRef;
import io.recordrelay.core.domain.TableRef;
import io.recordrelay.core.spi.ConnectorRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scans a source database schema and produces a column-level masking coverage report.
 *
 * <p>Each column is classified as:
 *
 * <ul>
 *   <li>{@code EXPOSED} — column name matches a known PII pattern but no masking rule covers it
 *   <li>{@code MASKED} — a masking rule is configured for this column
 *   <li>{@code CLEAN} — no PII signal detected and no masking rule
 * </ul>
 */
public final class MaskingCoverageEngine {

  /** Column name fragments that suggest PII content (lower-cased for comparison). */
  private static final Set<String> HIGH_RISK_PATTERNS =
      Set.of(
          "email",
          "e_mail",
          "email_address",
          "email_addr",
          "mail",
          "phone",
          "phone_number",
          "mobile",
          "cell",
          "telephone",
          "tel",
          "address",
          "street",
          "street_address",
          "addr",
          "ssn",
          "social_security",
          "national_id",
          "nin",
          "tax_id",
          "identity_number",
          "iban",
          "bank_account",
          "account_number",
          "card_number",
          "credit_card",
          "cvv",
          "password",
          "passwd",
          "secret",
          "api_key",
          "dob",
          "date_of_birth",
          "birth_date",
          "birthday");

  private static final Set<String> LOW_RISK_PATTERNS =
      Set.of(
          "first_name",
          "last_name",
          "full_name",
          "surname",
          "given_name",
          "ip_address",
          "ip",
          "username",
          "user_name");

  /** Single column entry in the coverage report. */
  public record CoverageEntry(
      String tableName,
      String columnName,
      String columnType,
      CoverageStatus status,
      String piiCategory) {}

  /** Coverage status of a column. */
  public enum CoverageStatus {
    EXPOSED,
    MASKED,
    LOW_RISK,
    CLEAN
  }

  /** Full coverage report for a database. */
  public record CoverageReport(
      String connectionName,
      List<CoverageEntry> entries,
      int exposedCount,
      int maskedCount,
      int lowRiskCount,
      int totalColumns) {

    public double coveragePct() {
      if (totalColumns == 0) return 100.0;
      return (maskedCount * 100.0) / (maskedCount + exposedCount);
    }
  }

  private MaskingCoverageEngine() {}

  /**
   * Inspects all tables of {@code profile} and classifies each column against {@code
   * maskingConfig}.
   *
   * @param profile source connection
   * @param maskingConfig masking rules to check coverage against (may be none)
   * @param connName display name for the report header
   */
  public static CoverageReport analyse(
      ConnectionProfile profile, MaskingConfig maskingConfig, String connName) throws Exception {

    var connector = ConnectorRegistry.findConnector(profile);
    var inspector = connector.schemaInspector();
    var dbRef = new DatabaseRef(profile.database(), profile.type());
    var tables = inspector.listTables(profile, dbRef);

    var entries = new ArrayList<CoverageEntry>();
    int exposed = 0, masked = 0, lowRisk = 0;

    for (TableRef table : tables) {
      List<ColumnMeta> columns;
      try {
        columns = inspector.inspectColumns(profile, table);
      } catch (Exception e) {
        continue; // skip tables we can't inspect
      }
      for (ColumnMeta col : columns) {
        var colLower = col.name().toLowerCase(Locale.ROOT);
        boolean isMasked = maskingConfig.ruleFor(col.name()).isPresent();
        String piiCategory = detectPiiCategory(colLower);
        boolean isHighRisk = isHighRisk(colLower);
        boolean isLowRisk = !isHighRisk && isLowRisk(colLower);

        CoverageStatus status;
        if (isMasked) {
          status = CoverageStatus.MASKED;
          masked++;
        } else if (isHighRisk) {
          status = CoverageStatus.EXPOSED;
          exposed++;
        } else if (isLowRisk) {
          status = CoverageStatus.LOW_RISK;
          lowRisk++;
        } else {
          status = CoverageStatus.CLEAN;
        }
        entries.add(
            new CoverageEntry(
                table.tableName(), col.name(), col.nativeType(), status, piiCategory));
      }
    }

    return new CoverageReport(connName, entries, exposed, masked, lowRisk, entries.size());
  }

  private static boolean isHighRisk(String colLower) {
    return HIGH_RISK_PATTERNS.stream().anyMatch(p -> colLower.equals(p) || colLower.contains(p));
  }

  private static boolean isLowRisk(String colLower) {
    return LOW_RISK_PATTERNS.stream().anyMatch(p -> colLower.equals(p) || colLower.contains(p));
  }

  private static String detectPiiCategory(String colLower) {
    if (colLower.contains("email") || colLower.contains("mail")) return "EMAIL";
    if (colLower.contains("phone") || colLower.contains("mobile") || colLower.contains("tel"))
      return "PHONE";
    if (colLower.contains("address") || colLower.contains("street")) return "ADDRESS";
    if (colLower.contains("iban") || colLower.contains("bank") || colLower.contains("card"))
      return "FINANCIAL";
    if (colLower.contains("ssn")
        || colLower.contains("national_id")
        || colLower.contains("tax_id")
        || colLower.contains("identity")) return "NATIONAL_ID";
    if (colLower.contains("password")
        || colLower.contains("secret")
        || colLower.contains("api_key")) return "CREDENTIAL";
    if (colLower.contains("birth") || colLower.contains("dob")) return "BIRTH_DATE";
    if (colLower.contains("name")) return "NAME";
    if (colLower.contains("ip")) return "IP";
    return "";
  }
}

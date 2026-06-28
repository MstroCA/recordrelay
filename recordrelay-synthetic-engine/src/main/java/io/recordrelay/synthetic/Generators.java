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
package io.recordrelay.synthetic;

import io.recordrelay.core.domain.ColumnMeta;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.UUID;

/**
 * Factory for {@link ColumnValueGenerator} instances.
 *
 * <p>Resolution order: (1) column name heuristic, (2) native type, (3) generic fallback.
 */
public final class Generators {

  // ── Name word lists ───────────────────────────────────────────────────────

  private static final String[] FIRST_NAMES = {
    "Alice", "Bob", "Carol", "David", "Eva", "Frank", "Grace", "Henry", "Irene", "James",
    "Karen", "Leo", "Maya", "Nathan", "Olivia", "Paul", "Quinn", "Rachel", "Sam", "Tina"
  };

  private static final String[] LAST_NAMES = {
    "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis", "Wilson",
    "Moore", "Taylor", "Anderson", "Thomas", "Jackson", "White", "Harris", "Martin", "Thompson"
  };

  private static final String[] DOMAINS = {
    "gmail.com", "yahoo.com", "outlook.com", "example.com", "test.org", "mail.net"
  };

  private static final String[] STREETS = {
    "Main St",
    "Oak Ave",
    "Elm Rd",
    "Park Blvd",
    "Cedar Ln",
    "Maple Dr",
    "Pine Way",
    "River Rd",
    "Lake Ave",
    "Hill St"
  };

  private static final String[] CITIES = {
    "Springfield", "Shelbyville", "Capital City", "Ogdenville", "North Haverbrook",
    "Brockway", "Greenville", "Riverside", "Franklin", "Madison"
  };

  private static final String[] COUNTRIES = {
    "US", "GB", "DE", "FR", "TR", "CA", "AU", "JP", "BR", "IN"
  };

  private static final String[] CURRENCIES = {"USD", "EUR", "GBP", "JPY", "TRY", "CAD", "AUD"};

  private static final String[] STATUSES = {"active", "inactive", "pending", "archived"};

  private Generators() {}

  /** Selects the best generator for the given column metadata. */
  public static ColumnValueGenerator forColumn(ColumnMeta col, int startId) {
    String name = col.name().toLowerCase();
    String type = col.nativeType().toLowerCase();

    // ── Primary key / sequential id ──────────────────────────────────────
    if (col.primaryKey() || name.equals("id")) {
      return (row, rng) -> (long) (startId + row);
    }

    // ── UUID columns ─────────────────────────────────────────────────────
    if (name.contains("uuid") || name.contains("guid") || type.contains("uuid")) {
      return (row, rng) -> UUID.randomUUID().toString();
    }

    // ── Name heuristics ───────────────────────────────────────────────────
    if (name.equals("first_name") || name.equals("firstname") || name.equals("given_name")) {
      return (row, rng) -> pick(FIRST_NAMES, rng);
    }
    if (name.equals("last_name")
        || name.equals("lastname")
        || name.equals("surname")
        || name.equals("family_name")) {
      return (row, rng) -> pick(LAST_NAMES, rng);
    }
    if (name.equals("name")
        || name.equals("full_name")
        || name.equals("fullname")
        || name.equals("customer_name")
        || name.equals("username")) {
      return (row, rng) -> pick(FIRST_NAMES, rng) + " " + pick(LAST_NAMES, rng);
    }

    // ── Contact heuristics ────────────────────────────────────────────────
    if (name.contains("email")) {
      return (row, rng) -> {
        String first = pick(FIRST_NAMES, rng).toLowerCase();
        String last = pick(LAST_NAMES, rng).toLowerCase();
        return first + "." + last + (rng.nextInt(900) + 100) + "@" + pick(DOMAINS, rng);
      };
    }
    if (name.contains("phone") || name.contains("mobile") || name.contains("tel")) {
      return (row, rng) ->
          "+1-"
              + (200 + rng.nextInt(800))
              + "-"
              + (100 + rng.nextInt(900))
              + "-"
              + String.format("%04d", rng.nextInt(10000));
    }

    // ── Address heuristics ────────────────────────────────────────────────
    if (name.contains("address") || name.equals("street") || name.equals("addr")) {
      return (row, rng) -> (rng.nextInt(9999) + 1) + " " + pick(STREETS, rng);
    }
    if (name.equals("city")) {
      return (row, rng) -> pick(CITIES, rng);
    }
    if (name.equals("country") || name.equals("country_code")) {
      return (row, rng) -> pick(COUNTRIES, rng);
    }
    if (name.equals("postal_code")
        || name.equals("zip")
        || name.equals("zip_code")
        || name.equals("postcode")) {
      return (row, rng) -> String.format("%05d", rng.nextInt(100000));
    }

    // ── Financial heuristics ──────────────────────────────────────────────
    if (name.equals("currency") || name.equals("currency_code")) {
      return (row, rng) -> pick(CURRENCIES, rng);
    }
    if (name.contains("price")
        || name.contains("amount")
        || name.contains("total")
        || name.contains("balance")
        || name.contains("cost")) {
      return (row, rng) ->
          BigDecimal.valueOf(rng.nextInt(100000) / 100.0)
              .setScale(2, java.math.RoundingMode.HALF_UP);
    }
    if (name.contains("quantity")
        || name.equals("qty")
        || name.contains("count")
        || name.contains("stock")) {
      return (row, rng) -> rng.nextInt(1000);
    }

    // ── Status / state ────────────────────────────────────────────────────
    if (name.equals("status") || name.equals("state")) {
      return (row, rng) -> pick(STATUSES, rng);
    }
    if (name.contains("active") || name.contains("enabled") || name.contains("is_")) {
      return (row, rng) -> rng.nextBoolean();
    }

    // ── FK id columns (non-PK "xxx_id") ──────────────────────────────────
    if (name.endsWith("_id") && !col.primaryKey()) {
      return (row, rng) -> (long) (rng.nextInt(1000) + 1);
    }

    // ── Date / time heuristics ────────────────────────────────────────────
    if (name.contains("created")
        || name.contains("updated")
        || name.contains("modified")
        || name.contains("timestamp")
        || name.contains("at") && type.contains("time")) {
      long now = Instant.now().getEpochSecond();
      return (row, rng) -> Instant.ofEpochSecond(now - rng.nextInt(365 * 24 * 3600)).toString();
    }
    if (name.contains("birth") || name.contains("dob")) {
      return (row, rng) ->
          LocalDate.now()
              .minus(18 + rng.nextInt(60), ChronoUnit.YEARS)
              .minus(rng.nextInt(365), ChronoUnit.DAYS)
              .toString();
    }
    if (name.contains("date")) {
      return (row, rng) -> LocalDate.now().minus(rng.nextInt(1825), ChronoUnit.DAYS).toString();
    }

    // ── Description / notes ────────────────────────────────────────────────
    if (name.contains("description")
        || name.contains("notes")
        || name.contains("comment")
        || name.contains("remark")
        || name.contains("bio")) {
      return (row, rng) -> "Synthetic " + name + " for row " + row;
    }

    // ── Type-based fallbacks ──────────────────────────────────────────────
    return forType(type, col.nullable());
  }

  private static ColumnValueGenerator forType(String type, boolean nullable) {
    if (type.contains("bool")) {
      return (row, rng) -> rng.nextBoolean();
    }
    if (type.contains("int")
        || type.contains("serial")
        || type.contains("number")
        || type.contains("numeric") && !type.contains("decimal")) {
      return (row, rng) -> rng.nextInt(100000);
    }
    if (type.contains("float") || type.contains("double") || type.contains("real")) {
      return (row, rng) -> Math.round(rng.nextDouble() * 10000.0) / 100.0;
    }
    if (type.contains("decimal") || type.contains("numeric") || type.contains("money")) {
      return (row, rng) ->
          BigDecimal.valueOf(rng.nextInt(1000000) / 100.0)
              .setScale(2, java.math.RoundingMode.HALF_UP);
    }
    if (type.contains("timestamp") || type.contains("datetime")) {
      long now = Instant.now().getEpochSecond();
      return (row, rng) -> Instant.ofEpochSecond(now - rng.nextInt(365 * 24 * 3600)).toString();
    }
    if (type.contains("date")) {
      return (row, rng) -> LocalDate.now().minus(rng.nextInt(1825), ChronoUnit.DAYS).toString();
    }
    if (type.contains("uuid")) {
      return (row, rng) -> UUID.randomUUID().toString();
    }
    // VARCHAR / TEXT / CHAR / CLOB / etc.
    return (row, rng) -> "value_" + row + "_" + Integer.toHexString(rng.nextInt(0xFFFF));
  }

  private static String pick(String[] arr, Random rng) {
    return arr[rng.nextInt(arr.length)];
  }
}

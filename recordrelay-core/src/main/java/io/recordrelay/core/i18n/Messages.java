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
package io.recordrelay.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Central access point for localised UI strings.
 *
 * <p>Bundles are loaded from the classpath resource {@code
 * io/recordrelay/core/i18n/messages[_locale].properties}. The active locale defaults to the JVM
 * system locale and can be overridden at startup via {@link #setLocale(Locale)}.
 *
 * <p>Use {@link #get(String)} for simple lookups and {@link #get(String, Object...)} for {@link
 * MessageFormat}-style parameterised strings ({@code {0}}, {@code {1}} …).
 */
public final class Messages {

  private static final String BUNDLE_BASE = "io.recordrelay.core.i18n.messages";

  private static volatile Locale activeLocale = Locale.getDefault();
  private static volatile ResourceBundle bundle = null;

  private Messages() {}

  /**
   * Sets the active locale and invalidates the cached bundle.
   *
   * @param locale the locale to use for all subsequent {@link #get} calls
   */
  public static synchronized void setLocale(Locale locale) {
    activeLocale = locale;
    bundle = null;
  }

  /**
   * Returns the active {@link ResourceBundle}, loading it lazily if needed.
   *
   * @return the resource bundle for the active locale
   */
  public static ResourceBundle getBundle() {
    if (bundle == null) {
      synchronized (Messages.class) {
        if (bundle == null) {
          bundle = loadBundle(activeLocale);
        }
      }
    }
    return bundle;
  }

  /**
   * Returns the localised string for {@code key}.
   *
   * @param key the message key
   * @return the localised string, or {@code "!key!"} if the key is missing
   */
  public static String get(String key) {
    try {
      return getBundle().getString(key);
    } catch (MissingResourceException e) {
      return "!" + key + "!";
    }
  }

  /**
   * Returns the localised string for {@code key} with {@link MessageFormat} substitution.
   *
   * @param key the message key
   * @param args positional arguments ({@code {0}}, {@code {1}} …)
   * @return the formatted string, or {@code "!key!"} if the key is missing
   */
  public static String get(String key, Object... args) {
    String pattern = get(key);
    if (pattern.startsWith("!") && pattern.endsWith("!")) {
      return pattern;
    }
    return MessageFormat.format(pattern, args);
  }

  private static ResourceBundle loadBundle(Locale locale) {
    try {
      ResourceBundle rb = ResourceBundle.getBundle(BUNDLE_BASE, locale);
      // Guard against Java's default-locale fallback: if the bundle's locale doesn't match
      // the requested language (e.g. "en" silently resolved to the JVM's "tr" default),
      // fall back to the base bundle explicitly.
      if (!locale.getLanguage().isEmpty()
          && !rb.getLocale().getLanguage().equals(locale.getLanguage())) {
        return ResourceBundle.getBundle(BUNDLE_BASE, Locale.ROOT);
      }
      return rb;
    } catch (MissingResourceException e) {
      return ResourceBundle.getBundle(BUNDLE_BASE, Locale.ROOT);
    }
  }
}

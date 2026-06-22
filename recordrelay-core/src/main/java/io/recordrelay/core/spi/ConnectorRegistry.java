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
package io.recordrelay.core.spi;

import io.recordrelay.core.domain.ConnectionProfile;
import io.recordrelay.core.exception.NoConnectorFoundException;
import io.recordrelay.core.port.out.ContextProviderPort;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers and provides access to registered {@link ContextProviderPort} implementations via
 * {@link ServiceLoader}.
 *
 * <p>Connectors register themselves by placing their fully-qualified class name in {@code
 * META-INF/services/io.recordrelay.core.port.out.ContextProviderPort} within their JAR.
 *
 * <p><strong>IntelliJ plugin note:</strong> the plugin sandbox isolates classloaders, so {@link
 * #loadFrom(ClassLoader)} must be used instead of the static registry methods to discover
 * connectors bundled with the plugin.
 */
public final class ConnectorRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(ConnectorRegistry.class);

  private static volatile ServiceLoader<ContextProviderPort> loader =
      ServiceLoader.load(ContextProviderPort.class);

  private ConnectorRegistry() {}

  /**
   * Returns the first connector that supports the database type of {@code profile}.
   *
   * @param profile the connection profile for which a connector is needed
   * @return a matching connector
   * @throws NoConnectorFoundException if no registered connector matches the profile's type
   */
  public static ContextProviderPort findConnector(ConnectionProfile profile) {
    return StreamSupport.stream(loader.spliterator(), false)
        .filter(c -> c.supports(profile))
        .findFirst()
        .orElseThrow(() -> new NoConnectorFoundException(profile.type()));
  }

  /**
   * Returns an unmodifiable list of all connectors currently registered on the classpath.
   *
   * @return all registered connectors
   */
  public static List<ContextProviderPort> allConnectors() {
    return StreamSupport.stream(loader.spliterator(), false)
        .collect(java.util.stream.Collectors.toUnmodifiableList());
  }

  /**
   * Reloads the ServiceLoader, picking up any connector JARs added to the classpath at runtime.
   *
   * <p>This method is thread-safe. It is not needed in typical usage where all connector JARs are
   * present on the classpath at startup.
   */
  public static synchronized void reload() {
    loader = ServiceLoader.load(ContextProviderPort.class);
    LOG.debug("ConnectorRegistry reloaded; {} connector(s) available", allConnectors().size());
  }

  /**
   * Reloads the ServiceLoader using the given classloader.
   *
   * <p>Required in IntelliJ plugin context: the plugin classloader is isolated from the platform
   * classloader, so the default static {@code loader} cannot discover connector JARs bundled inside
   * the plugin. Call this once at plugin startup with {@code MyService.class.getClassLoader()}.
   *
   * @param classLoader the classloader that can see the connector JARs
   */
  public static synchronized void reloadFrom(ClassLoader classLoader) {
    loader = ServiceLoader.load(ContextProviderPort.class, classLoader);
    LOG.debug(
        "ConnectorRegistry reloaded from classloader; {} connector(s) available",
        allConnectors().size());
  }

  /**
   * Discovers connectors from the given classloader.
   *
   * <p>Required in IntelliJ plugin context where the plugin classloader is isolated from the
   * platform classloader and the standard static {@code loader} cannot find plugin-bundled JARs.
   *
   * @param classLoader the classloader to search for connector implementations
   * @return all connectors discoverable via the given classloader
   */
  public static List<ContextProviderPort> loadFrom(ClassLoader classLoader) {
    var pluginLoader = ServiceLoader.load(ContextProviderPort.class, classLoader);
    return StreamSupport.stream(pluginLoader.spliterator(), false)
        .collect(java.util.stream.Collectors.toUnmodifiableList());
  }
}

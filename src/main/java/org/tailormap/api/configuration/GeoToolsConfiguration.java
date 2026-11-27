/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import jakarta.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.geotools.api.data.DataAccessFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.jdbc.FilterToSQL;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.AttributeTypeBuilder;
import org.geotools.feature.FeatureTypes;
import org.geotools.feature.visitor.UniqueVisitor;
import org.geotools.filter.FunctionFinder;
import org.geotools.filter.FunctionImpl;
import org.geotools.filter.expression.PropertyAccessors;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.jdbc.SQLDialect;
import org.geotools.util.Converters;
import org.geotools.util.PreventLocalEntityResolver;
import org.geotools.util.factory.GeoTools;
import org.geotools.util.factory.Hints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.tailormap.api.geotools.http.NoExpectContinueHttpClientFactory;

@Configuration
public class GeoToolsConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @PostConstruct
  public void init() {
    Hints hints = new Hints(Hints.ENTITY_RESOLVER, PreventLocalEntityResolver.INSTANCE);
    // Don't wait up to 3 seconds for a "100 Continue" response on every POST request, see
    // NoExpectContinueHttpClient
    hints.put(Hints.HTTP_CLIENT_FACTORY, NoExpectContinueHttpClientFactory.class);
    GeoTools.init(hints);

    warmUpFactories();

    if (logger.isTraceEnabled()) {
      logger.trace("GeoTools initialised: {}", GeoTools.getAboutInfo());
      logger.trace("GeoTools default hints: {}", GeoTools.getDefaultHints());
    }
  }

  /**
   * Initialize GeoTools factories and classes with static initializers that use them on a single thread at startup,
   * before any request can use GeoTools.
   *
   * <p>Without this, the first concurrent requests can deadlock permanently on a class initialization lock cycle:
   *
   * <ul>
   *   <li>Thread A creates a filter function: {@code DefaultFunctionFactory.loadFunctions()} calls the {@code static
   *       synchronized CommonFactoryFinder.getFunctions()}, which holds the {@code CommonFactoryFinder} class monitor
   *       while scanning for {@code Function} plugins and waits for the class initialization of {@code FunctionImpl}
   *       (their superclass);
   *   <li>Thread B opens a JDBC datastore: {@code SQLDialect.<clinit>} initializes {@code InFunction} and so
   *       {@code FunctionImpl}, whose static initializer calls {@code CommonFactoryFinder.getFilterFactory()} and
   *       waits for the {@code CommonFactoryFinder} class monitor held by thread A.
   * </ul>
   *
   * Other threads then block on the same monitor while holding database transactions, until the connection pool runs
   * dry.
   *
   * <p>The other plugin scans GeoTools does while holding a class monitor, and classes with static initializers that
   * call {@code CommonFactoryFinder} which would otherwise first be initialized by requests (found with
   * {@code -Xlog:class+init}), are warmed up as well. No other lock cycle was found with these, but this is cheap and
   * also moves the plugin scanning cost from the first requests to startup.
   */
  static void warmUpFactories() {
    CommonFactoryFinder.getFilterFactory(null);
    // Its static initializer calls CommonFactoryFinder.getFilterFactory()
    initializeClass(FunctionImpl.class);
    // Scans for all function plugins and fills the function cache of DefaultFunctionFactory
    int functionCount =
        new FunctionFinder(null).getAllFunctionDescriptions().size();
    // Otherwise initialized (together with InFunction) when the first JDBC datastore is created
    initializeClass(SQLDialect.class);

    // Other plugin scans done while holding a class monitor, otherwise done by the first requests
    CommonFactoryFinder.getFeatureTypeFactory(null);
    CommonFactoryFinder.getFeatureFactory(null);
    DataAccessFinder.getAllDataStores().forEachRemaining(factory -> {});
    Converters.getConverterFactories(null).forEach(factory -> {});
    JTSFactoryFinder.getGeometryFactory();
    // Static initializers calling CommonFactoryFinder or scanning for plugins
    for (Class<?> clazz : List.of(
        AttributeTypeBuilder.class,
        DataUtilities.class,
        FeatureTypes.class,
        UniqueVisitor.class,
        FilterToSQL.class,
        PropertyAccessors.class)) {
      initializeClass(clazz);
    }
    logger.debug("GeoTools factories initialized, {} filter functions available", functionCount);
  }

  private static void initializeClass(Class<?> clazz) {
    try {
      Class.forName(clazz.getName(), true, clazz.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }
}

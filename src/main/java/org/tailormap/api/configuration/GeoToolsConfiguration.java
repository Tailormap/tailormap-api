/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import jakarta.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import org.geotools.util.factory.GeoTools;
import org.geotools.util.factory.Hints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.tailormap.api.geotools.TMPreventLocalEntityResolver;

@Configuration
public class GeoToolsConfiguration {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @PostConstruct
  public void init() {
    GeoTools.init(new Hints(Hints.ENTITY_RESOLVER, TMPreventLocalEntityResolver.INSTANCE));
    if (logger.isTraceEnabled()) {
      logger.trace("GeoTools initialised: {}", GeoTools.getAboutInfo());
      logger.trace("GeoTools default hints: {}", GeoTools.getDefaultHints());
    }
  }
}

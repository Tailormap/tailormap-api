/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.geotools.filter.FunctionFinder;
import org.junit.jupiter.api.Test;

/**
 * The class initialization deadlock {@link GeoToolsConfiguration#warmUpFactories()} prevents only happens with
 * concurrent first use, which can't be reproduced reliably in a test. This only checks the warm-up itself works and
 * leaves the function lookup working.
 */
class GeoToolsConfigurationTest {
  @Test
  void warm_up_factories_loads_filter_functions() {
    assertDoesNotThrow(GeoToolsConfiguration::warmUpFactories);
    FunctionFinder functionFinder = new FunctionFinder(null);
    // Function used by UniqueValuesHelper
    assertNotNull(functionFinder.findFunctionDescription("Collection_Unique"));
    // Function initialized by SQLDialect via InFunction
    assertNotNull(functionFinder.findFunctionDescription("in"));
  }
}

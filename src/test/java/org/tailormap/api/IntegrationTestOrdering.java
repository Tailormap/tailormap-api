/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api;

/**
 * Specify the order for specific integration tests, to accommodate for some tests modifying test data. This is needed
 * to ensure that e.g. the Prometheus integration tests run after other integration tests.
 */
public final class IntegrationTestOrdering {
  private IntegrationTestOrdering() {
    // Utility class; prevent instantiation.
  }

  public static final int FIRST_INTEGRATION_TEST_ORDER = 1;
  public static final int SECOND_INTEGRATION_TEST_ORDER = 2;
  public static final int PROMETHEUS_UNHAPPY_INTEGRATION_TEST_ORDER = Integer.MAX_VALUE - 10;
  public static final int PROMETHEUS_INTEGRATION_TEST_ORDER = Integer.MAX_VALUE - 1;
  public static final int APPLICATION_EVENT_HANDLER_INTEGRATION_TEST_ORDER = Integer.MAX_VALUE;
  // should be last test to prevent side effects - as some data may be deleted
  public static final int EDIT_FEATURES_CONTROLLER_INTEGRATION_TEST_ORDER = Integer.MAX_VALUE;
}

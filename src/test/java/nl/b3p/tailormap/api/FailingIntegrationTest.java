/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class FailingIntegrationTest {
  @Test
  void failing() {
    fail("Just for testing a failed test");
  }
}

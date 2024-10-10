/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TMJobDataMapTest {

  /** Test the creation using a map with missing required parameters. */
  @Test
  void testInvalidMap() {
    assertThrows(IllegalArgumentException.class, () -> new TMJobDataMap(Map.of("type", "test")));
  }

  /** Test the creation using a map with all required parameters. */
  @Test
  void testMap() {
    TMJobDataMap jobDataMap = new TMJobDataMap(Map.of("type", "test", "description", "test"));
    assertNotNull(jobDataMap);
    assertEquals("NONE", jobDataMap.getStatus().name());
    assertEquals(5, jobDataMap.getPriority());
  }

  /** Test the creation using a map with required parameters and negative priority. */
  @Test
  void testMapWithPriority() {
    TMJobDataMap jobDataMap =
        new TMJobDataMap(Map.of("type", "test", "description", "test", "priority", -1));
    assertNotNull(jobDataMap);
    assertEquals("NONE", jobDataMap.getStatus().name());
    assertEquals(0, jobDataMap.getPriority());
  }
}

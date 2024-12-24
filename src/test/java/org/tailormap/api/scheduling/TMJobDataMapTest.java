/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TMJobDataMapTest {

  /** Test the creation using a map with missing required parameters. */
  @Test
  void testInvalidMap() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TMJobDataMap(Map.of(Task.TYPE_KEY, "test")),
        "JobDataMap should not be created for missing parameters");
  }

  /** Test the creation using a map with all required parameters. */
  @Test
  void testMap() {
    TMJobDataMap jobDataMap = new TMJobDataMap(Map.of(Task.TYPE_KEY, "test", Task.DESCRIPTION_KEY, "test"));
    assertNotNull(jobDataMap, "JobDataMap should not be null");
    assertEquals("NONE", jobDataMap.getState().name(), "State should be NONE");
    assertEquals(5, jobDataMap.getPriority(), "Priority should be 5");
  }

  /** Test the creation using a map with required parameters and negative priority. */
  @Test
  void testMapWithPriority() {
    TMJobDataMap jobDataMap =
        new TMJobDataMap(Map.of(Task.TYPE_KEY, "test", Task.DESCRIPTION_KEY, "test", Task.PRIORITY_KEY, -1));
    assertNotNull(jobDataMap, "JobDataMap should not be null");
    assertEquals("NONE", jobDataMap.getState().name(), "State should be NONE");
    assertEquals(0, jobDataMap.getPriority(), "Priority should be 0");
  }
}

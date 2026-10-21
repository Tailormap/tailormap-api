/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api;

import tools.jackson.databind.json.JsonMapper;

/**
 * Build JSON for tests from basic Java structures such as {@code Map.of(...)} and {@code List.of(...)} instead of JSON
 * strings, so values do not need to be escaped or concatenated and comparisons do not depend on formatting.
 */
public final class TestJson {
  private TestJson() {}

  /**
   * Serialize a value to JSON.
   *
   * @param value a basic Java structure (for example nested maps and lists) or a model object
   * @return the JSON string
   */
  public static String json(Object value) {
    return JsonMapper.shared().writeValueAsString(value);
  }
}

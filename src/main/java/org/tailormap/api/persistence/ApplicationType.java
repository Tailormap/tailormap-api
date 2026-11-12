/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.EnumeratedValue;

public enum ApplicationType {
  APPLICATION("application"),
  STORIES("stories");

  @EnumeratedValue
  private final String value;

  ApplicationType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return getValue();
  }
}

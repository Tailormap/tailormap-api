/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

public enum TaskType {
  INDEX("index"),
  PROMETHEUS_PING("prometheus_ping"),
  ;

  private final String value;

  TaskType(String value) {
    this.value = value;
  }

  public static TaskType fromValue(String value) {
    for (TaskType type : TaskType.values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unexpected value '%s'".formatted(value));
  }

  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }
}

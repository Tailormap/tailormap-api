/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import org.quartz.Trigger;

/** Define a map with minimally required job data for the TailorMap scheduler. */
public class TMJobDataMap extends HashMap<String, Object> {

  /**
   * Create a new instance of TMJobDataMap.
   *
   * @param map the map with job data, must have values for the required parameters {@code type} and
   *     {@code description}
   * @throws NullPointerException if the map does not contain the required parameters
   */
  public TMJobDataMap(Map<String, Object> map) throws NullPointerException {
    // Check if the map contains the required parameters
    this(map.get("type").toString(), map.get("description").toString());
    this.putAll(map);
  }

  public TMJobDataMap(@NotNull String type, @NotNull String description) {
    this(type, description, Trigger.TriggerState.NONE);
  }

  public TMJobDataMap(
      @NotNull String type, @NotNull String description, @NotNull Trigger.TriggerState status) {
    super();
    super.put("type", type);
    super.put("description", description);
    super.put("status", status);
  }

  @NotNull
  public String getType() {
    return super.get("type").toString();
  }

  @NotNull
  public String getDescription() {
    return super.get("description").toString();
  }

  @NotNull
  public Trigger.TriggerState getStatus() {
    return (Trigger.TriggerState) super.get("status");
  }

  public void setStatus(Trigger.TriggerState status) {
    if (null == status) {
      status = Trigger.TriggerState.NONE;
    }
    super.put("status", status);
  }
}

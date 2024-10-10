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
import org.springframework.util.Assert;

/** Define a map with minimally required job data for the TailorMap scheduler. */
public class TMJobDataMap extends HashMap<String, Object> {

  /**
   * Create a new instance of TMJobDataMap.
   *
   * @param map the map with job data, must have values for the required parameters {@code type} and
   *     {@code description}
   */
  public TMJobDataMap(Map<String, Object> map) {
    this((String) map.get("type"), (String) map.get("description"));
    this.putAll(map);
  }

  /**
   * Create a new instance of TMJobDataMap with a status of {@code Trigger.TriggerState.NONE}.
   *
   * @param type the type of the job
   * @param description a description of the job
   */
  public TMJobDataMap(@NotNull String type, @NotNull String description) {
    this(type, description, Trigger.TriggerState.NONE);
  }

  /**
   * Create a new instance of TMJobDataMap.
   *
   * @param type the type of the job
   * @param description a description of the job
   * @param status the status of the job
   */
  public TMJobDataMap(
      @NotNull String type, @NotNull String description, @NotNull Trigger.TriggerState status) {
    super();
    // Check if the map contains the required parameters
    Assert.notNull(type, "type must not be null");
    Assert.notNull(description, "description must not be null");
    Assert.notNull(status, "status must not be null");
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

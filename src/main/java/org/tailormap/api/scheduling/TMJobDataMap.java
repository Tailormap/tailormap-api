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
    this((String) map.get(Task.TYPE_KEY), (String) map.get(Task.DESCRIPTION_KEY));
    this.putAll(map);
    // validate the priority
    this.setPriority((Integer) map.getOrDefault(Task.PRIORITY_KEY, Trigger.DEFAULT_PRIORITY));
  }

  /**
   * Create a new instance of TMJobDataMap with a status of {@code Trigger.TriggerState.NONE} and a
   * default priority.
   *
   * @param type the type of the job
   * @param description a description of the job
   */
  public TMJobDataMap(@NotNull String type, @NotNull String description) {
    this(type, description, Trigger.TriggerState.NONE);
  }

  /**
   * Create a new instance of TMJobDataMap with default priority.
   *
   * @param type the type of the job
   * @param description a description of the job
   * @param state the state of the job
   */
  public TMJobDataMap(
      @NotNull String type, @NotNull String description, @NotNull Trigger.TriggerState state) {
    this(type, description, state, Trigger.DEFAULT_PRIORITY);
  }

  /**
   * Create a new instance of TMJobDataMap.
   *
   * @param type the type of the job
   * @param description a description of the job
   * @param state the state of the job
   * @param priority the priority of the job, an integer value equal or greater than 0
   */
  public TMJobDataMap(
      @NotNull String type,
      @NotNull String description,
      @NotNull Trigger.TriggerState state,
      int priority) {
    super();
    // Check if the map contains the required parameters
    Assert.notNull(type, "type must not be null");
    Assert.notNull(description, "description must not be null");
    Assert.notNull(state, "state must not be null");
    super.put(Task.TYPE_KEY, type);
    super.put(Task.DESCRIPTION_KEY, description);
    super.put(Task.STATE_KEY, state);
    setPriority(priority);
  }

  @NotNull
  public String getType() {
    return super.get(Task.TYPE_KEY).toString();
  }

  @NotNull
  public String getDescription() {
    return super.get(Task.DESCRIPTION_KEY).toString();
  }

  @NotNull
  public Trigger.TriggerState getState() {
    return (Trigger.TriggerState) super.get(Task.STATE_KEY);
  }

  public void setState(Trigger.TriggerState state) {
    if (null == state) {
      state = Trigger.TriggerState.NONE;
    }
    super.put(Task.STATE_KEY, state);
  }

  /**
   * Set the priority of the job. Using this method will ensure that the priority is equal or
   * greater than 0.
   *
   * @param priority the priority of the job, an integer value equal or greater than 0
   */
  public void setPriority(int priority) {
    if (priority < 0) {
      priority = 0;
    }
    super.put(Task.PRIORITY_KEY, priority);
  }

  public int getPriority() {
    return (int) super.get(Task.PRIORITY_KEY);
  }
}

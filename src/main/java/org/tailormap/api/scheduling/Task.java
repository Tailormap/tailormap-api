/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

import org.quartz.InterruptableJob;

public interface Task {

  String TYPE_KEY = "type";
  String DESCRIPTION_KEY = "description";
  String UUID_KEY = "uuid";
  String CRON_EXPRESSION_KEY = "cronExpression";
  String PRIORITY_KEY = "priority";
  String STATE_KEY = "state";
  String LAST_RESULT_KEY = "lastResult";
  String INTERRUPTABLE_KEY = "interruptable";

  /**
   * Get the type of the task. Implement this method to return the key for the type of task. This
   * must be a read-only property.
   *
   * @return the type of task
   */
  TaskType getType();

  /**
   * Get the description of the task.
   *
   * @return the description
   */
  String getDescription();

  /**
   * Set the description of the task.
   *
   * @param description the description
   */
  void setDescription(String description);

  /**
   * Determine if this task can be stopped on demand (implements {@code InterruptableJob}).
   *
   * @return {@code true} if the task can be stopped on demand, false otherwise
   * @see InterruptableJob
   */
  default boolean isInterruptable() {
    return InterruptableJob.class.isAssignableFrom(this.getClass());
  }
}

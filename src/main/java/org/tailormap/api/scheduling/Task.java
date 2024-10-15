/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.scheduling;

public interface Task {

  String TYPE_KEY = "type";
  String DESCRIPTION_KEY = "description";
  String UUID_KEY = "uuid";
  String CRON_EXPRESSION_KEY = "cronExpression";
  String PRIORITY_KEY = "priority";
  String STATE_KEY = "state";

  /**
   * Get the type of the task. Implement this method to return a public static final String that is
   * a key for the type of task.
   *
   * @see org.tailormap.api.scheduling.PocTask#TYPE
   * @see org.tailormap.api.scheduling.IndexTask#TYPE
   * @return the type
   */
  String getType();
}

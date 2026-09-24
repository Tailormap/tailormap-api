/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import org.tailormap.api.admin.model.CapabilitiesLoadingEvent;

public interface CapabilitiesLoadingProgressReporting {
  /**
   * Emit a capabilities loading progress event to the event bus. An implementing class could use this method to
   * report progress of loading capabilities from a feature source on a SSE event bus or a logging framework.
   *
   * @param event The capabilities loading event to report
   */
  void reportCapabilitiesLoadingProgress(CapabilitiesLoadingEvent event);
}

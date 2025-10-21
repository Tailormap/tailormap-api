/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.listener;

import jakarta.persistence.PrePersist;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.tailormap.api.persistence.AdminRevision;

@Component
public class AdminRevisionListener {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @PrePersist
  public void prePersist(AdminRevision entity) {
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      logger.warn("No authentication info available, cannot set modifiedBy on revision entity");
      return;
    }
    logger.debug("Updating revision entity {}, with authentication info from: {}", entity, authentication);
    entity.setModifiedBy(authentication.getName());
  }
}

/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.listener;

import jakarta.persistence.PrePersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.tailormap.api.persistence.AdminRevisionEntity;

import java.lang.invoke.MethodHandles;
import java.util.Optional;

@Component
public class AdminRevisionListener {
    private static final Logger logger =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Optional<Authentication> authentication;

   public AdminRevisionListener(Optional<Authentication> authentication) {
       // we may need to use SecurityContextHolder.getContext().getAuthentication(); instead
    this.authentication = authentication;
  }

  @PrePersist
  public void prePersist(AdminRevisionEntity entity) {

  if (authentication.isEmpty()) {
        logger.warn("No authentication info available, cannot set modifiedBy on revision entity");
      return;
    }
      logger.info("Updating revision entity, with authentication info from: {}", authentication.get());
    entity.setModifiedBy(authentication.get().getName());
  }
}

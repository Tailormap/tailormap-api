/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import java.lang.invoke.MethodHandles;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.repository.TemporaryTokenRepository;

@Service
@Transactional
public class TemporaryTokenCleanupService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
  public void cleanExpiredTokens(TemporaryTokenRepository temporaryTokenRepository) {
    logger.trace("Running expired token cleanup...");
    long deletedCount =
        temporaryTokenRepository.deleteByExpirationTimeIsBefore(OffsetDateTime.now(ZoneId.systemDefault()));
    logger.trace("Cleaned up {} tokens", deletedCount);
  }
}

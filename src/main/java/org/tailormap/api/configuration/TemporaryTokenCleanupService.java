/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.repository.TemporaryTokenRepository;

@Service
public class TemporaryTokenCleanupService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final TemporaryTokenRepository temporaryTokenRepository;

  public TemporaryTokenCleanupService(TemporaryTokenRepository temporaryTokenRepository) {
    this.temporaryTokenRepository = temporaryTokenRepository;
  }

  @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
  @Transactional
  public void cleanExpiredTokens() {
    logger.trace("Running expired token cleanup...");
    temporaryTokenRepository.deleteByExpirationTimeBefore(ZonedDateTime.now(ZoneId.of("UTC")));
  }
}

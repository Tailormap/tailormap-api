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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for cleaning up expired temporary tokens from the database. This class uses
 * {@link org.springframework.jdbc.core.simple.JdbcClient} to execute the cleanup operation as we are not allowed to
 * inject a {@code Repository} in the scheduled method and when autowired into the class it does not provide a
 * transaction manager in the started thread.
 */
@Service
@Transactional
public class TemporaryTokenCleanupService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final JdbcClient jdbcClient;

  public TemporaryTokenCleanupService(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES, initialDelay = 1)
  public void cleanExpiredTokens() {
    logger.trace("Running expired token cleanup...");
    long deletedCount = this.jdbcClient
        .sql("DELETE FROM temporary_token WHERE expiration_time < ?")
        .param(OffsetDateTime.now(ZoneId.systemDefault()))
        .update();

    logger.trace("Cleaned up {} tokens", deletedCount);
  }
}

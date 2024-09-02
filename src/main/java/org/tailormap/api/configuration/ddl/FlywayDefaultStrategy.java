/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration.ddl;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayDefaultStrategy {
  private final FlywayMigrationResult migrationResult;

  public FlywayDefaultStrategy(FlywayMigrationResult migrationResult) {
    this.migrationResult = migrationResult;
  }

  @Bean
  public FlywayMigrationStrategy flywayMigrationStrategy() {
    return flyway -> this.migrationResult.setMigrateResult(flyway.migrate());
  }
}

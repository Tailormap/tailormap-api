/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.ddl;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@ConditionalOnProperty(name = "tailormap-api.database.start-with-new", havingValue = "true")
@Primary
public class FlywayStartWithNewSchema {
  private final FlywayMigrationResult migrationResult;

  public FlywayStartWithNewSchema(FlywayMigrationResult migrationResult) {
    this.migrationResult = migrationResult;
  }

  @Bean
  @Primary
  public FlywayMigrationStrategy flywayCleanMigrationStrategy() {
    return flyway -> {
      flyway.clean();
      this.migrationResult.setMigrateResult(flyway.migrate());
    };
  }
}

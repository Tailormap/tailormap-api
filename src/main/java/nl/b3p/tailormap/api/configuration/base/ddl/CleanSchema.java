/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base.ddl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CleanSchema {

  @Value("${tailormap-api.clean-database:false}")
  private boolean cleanDatabase;

  @Bean
  public FlywayMigrationStrategy flywayCleanMigrationStrategy() {
    return flyway -> {
      if (cleanDatabase) {
        flyway.clean();
      }
      flyway.migrate();
    };
  }
}

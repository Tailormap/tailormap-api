/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base.ddl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.lang.invoke.MethodHandles;

@Configuration
@Profile({"ddl && !ddl-update", "populate-test-database"})
public class CleanSchema {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  @Bean
  public FlywayMigrationStrategy flywayCleanMigrationStrategy() {
    return flyway -> {
      logger.info("Cleaning schema so full db initialization DDL script will be generated...");
      flyway.clean();
      flyway.migrate();
    };
  }
}

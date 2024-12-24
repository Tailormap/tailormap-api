/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.context.annotation.Configuration;
import org.tailormap.api.configuration.ddl.FlywayMigrationResult;
import org.tailormap.api.persistence.Catalog;
import org.tailormap.api.persistence.json.CatalogNode;
import org.tailormap.api.repository.CatalogRepository;
import org.tailormap.api.security.InternalAdminAuthentication;

/**
 * Migrate the database using JPA entities, after Flyway has migrated and the entity manager is initialized. When using
 * Flyway callbacks, we can't use JPA.
 */
@Configuration
public class TailormapDatabaseMigration {
  private final CatalogRepository catalogRepository;
  private final FlywayMigrationResult migrationResult;

  public TailormapDatabaseMigration(CatalogRepository catalogRepository, FlywayMigrationResult migrationResult) {
    this.catalogRepository = catalogRepository;
    this.migrationResult = migrationResult;
  }

  @PostConstruct
  public void databaseMigration() {

    MigrateResult migrateResult = migrationResult.getMigrateResult();

    if (migrateResult == null || migrateResult.migrationsExecuted == 0) {
      return;
    }

    // We can use the migration result to execute code when a specific migration has been executed
    // to upgrade old database contents, such as jsonb values.

    InternalAdminAuthentication.setInSecurityContext();
    try {
      if (migrateResult.migrations.stream().anyMatch(m -> "1".equals(m.version))) {
        createRootCatalog();
      }
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
  }

  private void createRootCatalog() {
    Catalog catalog = new Catalog()
        .setId(Catalog.MAIN)
        .setNodes(List.of(new CatalogNode().root(true).title("root").id("root")));
    catalogRepository.save(catalog);
  }
}

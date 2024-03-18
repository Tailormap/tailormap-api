/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;
import nl.b3p.tailormap.api.configuration.ddl.FlywayMigrationResult;
import nl.b3p.tailormap.api.persistence.Catalog;
import nl.b3p.tailormap.api.persistence.json.CatalogNode;
import nl.b3p.tailormap.api.repository.CatalogRepository;
import nl.b3p.tailormap.api.security.InternalAdminAuthentication;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.context.annotation.Configuration;

/**
 * Migrate the database using JPA entities, after Flyway has migrated and the entity manager is
 * initialized. When using Flyway callbacks, we can't use JPA.
 */
@Configuration
public class TailormapDatabaseMigration {
  private final CatalogRepository catalogRepository;
  private final FlywayMigrationResult migrationResult;

  public TailormapDatabaseMigration(
      CatalogRepository catalogRepository, FlywayMigrationResult migrationResult) {
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
    Catalog catalog =
        new Catalog()
            .setId(Catalog.MAIN)
            .setNodes(List.of(new CatalogNode().root(true).title("root").id("root")));
    catalogRepository.save(catalog);
  }
}

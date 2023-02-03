/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import nl.tailormap.viewer.config.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

/** Testcases for {@link ConfigurationRepository}. */
@ActiveProfiles("test")
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase
@EnableJpaRepositories(basePackages = {"nl.b3p.tailormap.api.repository"})
@EntityScan(
    basePackages = {
      "nl.tailormap.viewer.config",
      "nl.tailormap.viewer.config.app",
      "nl.tailormap.viewer.config.metadata",
      "nl.tailormap.viewer.config.security",
      "nl.tailormap.viewer.config.services"
    })
class ConfigurationRepositoryTest {

  @Autowired private ConfigurationRepository configurationRepository;

  @Test
  void it_should_findByConfigKeyDefaultApplication() {
    final Metadata m = configurationRepository.findByConfigKey(Metadata.DEFAULT_APPLICATION);
    assertNotNull(m, "we should have found something");
    assertEquals("1", m.getConfigValue(), "default application is not 1");
  }

  @Test
  void it_should_findByConfigKeyDatabaseVersion() {
    final Metadata m = configurationRepository.findByConfigKey(Metadata.DATABASE_VERSION_KEY);
    assertNotNull(m, "we should have found something");

    String databaseVersion = System.getenv("DATABASE_VERSION");
    assumeFalse(
        null == databaseVersion, "Database version unknown, should be set in system environment");
    assertEquals(databaseVersion, m.getConfigValue(), "version is not correct");
  }

  @Test
  void it_should_not_find_value_after_deleting_key() {
    configurationRepository.deleteMetadataByConfigKey(Metadata.DEFAULT_APPLICATION);
    final Metadata m = configurationRepository.findByConfigKey(Metadata.DEFAULT_APPLICATION);
    assertNull(m, "we should not have found anything");
  }
}

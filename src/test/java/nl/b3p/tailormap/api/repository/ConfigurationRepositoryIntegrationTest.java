/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import static nl.b3p.tailormap.api.persistence.Configuration.DEFAULT_APP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Configuration;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;

@PostgresIntegrationTest
@TestMethodOrder(OrderAnnotation.class)
class ConfigurationRepositoryIntegrationTest {

  @Autowired private ConfigurationRepository configurationRepository;

  @Test
  @Order(1)
  void it_should_findByConfigKeyDefaultApplication() {
    final Configuration c = configurationRepository.findByKey(DEFAULT_APP).orElse(null);
    assertNotNull(c);
    assertEquals("default", c.getValue());
  }

  @Test
  @Order(2)
  void it_should_not_find_value_after_deleting_key() {
    configurationRepository.deleteConfigurationByKey(DEFAULT_APP);
    final Configuration c = configurationRepository.findByKey(DEFAULT_APP).orElse(null);
    assertNull(c);
  }
}

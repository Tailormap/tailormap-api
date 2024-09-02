/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.tailormap.api.persistence.Configuration.DEFAULT_APP;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.security.InternalAdminAuthentication;

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
  @Transactional
  @Order(2)
  void it_should_not_find_value_after_deleting_key() {
    InternalAdminAuthentication.setInSecurityContext();
    try {
      configurationRepository.deleteConfigurationByKey(DEFAULT_APP);
      final Configuration c = configurationRepository.findByKey(DEFAULT_APP).orElse(null);
      assertNull(c);
    } finally {
      InternalAdminAuthentication.clearSecurityContextAuthentication();
    }
  }
}

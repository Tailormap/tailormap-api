/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@PostgresIntegrationTest
class ApplicationRepositoryIntegrationTest {
  @Autowired private ApplicationRepository applicationRepository;

  @Test
  void should_find_application_by_name() {
    Application a = applicationRepository.findByName("default");
    assertNotNull(a);
    assertEquals("default", a.getName(), "application name is incorrect");
  }

  @Test
  void should_not_find_application_by_nonexistent_name() {
    Application a = applicationRepository.findByName("does-not-exist");
    assertNull(a);
  }
}

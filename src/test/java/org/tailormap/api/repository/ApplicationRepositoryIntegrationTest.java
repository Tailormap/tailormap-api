/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Application;

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

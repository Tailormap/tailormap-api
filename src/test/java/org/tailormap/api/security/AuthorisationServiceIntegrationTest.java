/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.tailormap.api.persistence.Group.ADMIN;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.repository.ApplicationRepository;

@PostgresIntegrationTest
class AuthorisationServiceIntegrationTest {
  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  AuthorisationService authorisationService;

  @ParameterizedTest
  @ValueSource(strings = {"secured", "secured-auth"})
  @WithMockUser(
      username = "user",
      authorities = {"test-foo"},
      password = "user")
  void testUserAllowedToViewApplication(String applicationId) {
    Application app = applicationRepository.findByName(applicationId);
    assertNotNull(app, () -> "Application " + applicationId + " should exist");
    assertThat(
        "Application " + applicationId + " should have authorization rules",
        app.getAuthorizationRules().size(),
        greaterThan(0));
    assertTrue(authorisationService.userAllowedToViewApplication(app));
  }

  @ParameterizedTest
  @ValueSource(strings = {"secured", "secured-auth"})
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN},
      password = "tm-admin")
  void testAdminUserAllowedToViewApplication(String applicationId) {
    Application app = applicationRepository.findByName(applicationId);
    assertNotNull(app, () -> "Application " + applicationId + " should exist");
    assertThat(
        "Application " + applicationId + " should have authorization rules",
        app.getAuthorizationRules().size(),
        greaterThan(0));
    assertTrue(authorisationService.userAllowedToViewApplication(app));
  }

  @Test
  @WithMockUser(
      username = "user",
      authorities = {"test-baz"},
      password = "user")
  void testUserNotAllowedToViewApplication() {
    final String applicationId = "secured-auth";
    Application app = applicationRepository.findByName(applicationId);
    assertNotNull(app, () -> "Application " + applicationId + " should exist");
    assertThat(
        "Application " + applicationId + " should have authorization rules",
        app.getAuthorizationRules().size(),
        greaterThan(0));
    assertFalse(authorisationService.userAllowedToViewApplication(app));
  }

  @ParameterizedTest
  @ValueSource(strings = {"secured", "secured-auth"})
  void testAnonymousUserNotAllowedToViewApplication(String applicationId) {
    Application app = applicationRepository.findByName(applicationId);
    assertNotNull(app, () -> "Application " + applicationId + " should exist");
    assertThat(
        "Application " + applicationId + " should have authorization rules",
        app.getAuthorizationRules().size(),
        greaterThan(0));
    assertFalse(authorisationService.userAllowedToViewApplication(app));
  }
}

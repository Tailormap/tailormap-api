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
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.GeoServiceRepository;

@PostgresIntegrationTest
class AuthorisationServiceIntegrationTest {
  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private GeoServiceRepository geoServiceRepository;

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

  @Test
  @WithMockUser(
      username = "foo",
      authorities = {"test-foo"},
      password = "foo")
  void testFooUserNotAllowedToViewBGT() {
    final String applicationId = "secured-auth";
    Application app = applicationRepository.findByName(applicationId);
    assertNotNull(app, () -> "Application " + applicationId + " should exist");
    assertThat(
        "Application " + applicationId + " should have authorization rules",
        app.getAuthorizationRules().size(),
        greaterThan(0));
    assertTrue(authorisationService.userAllowedToViewApplication(app));
    // this id matches title "Test GeoServer (with authorization rules)"
    // this service is allowed for user with authority test-foo
    GeoService geoService =
        geoServiceRepository.findById("filtered-snapshot-geoserver").orElse(null);
    assertNotNull(geoService, () -> "GeoService should exist");
    assertTrue(authorisationService.userAllowedToViewGeoService(geoService));

    GeoServiceLayer bgtLayer = geoService.getLayers().stream()
        .filter(l -> "BGT".equals(l.getName()))
        .findFirst()
        .orElse(null);
    assertNotNull(bgtLayer, () -> "BGT layer should exist");
    // this layer is not allowed for user with authority test-foo
    assertFalse(authorisationService.userAllowedToViewGeoServiceLayer(geoService, bgtLayer));

    // this layer is allowed for user with authority test-foo
    GeoServiceLayer terreindeelLayer = geoService.getLayers().stream()
        .filter(l -> "postgis:begroeidterreindeel".equals(l.getName()))
        .findFirst()
        .orElse(null);
    assertNotNull(terreindeelLayer, () -> "begroeidterreindeel layer should exist");
    assertTrue(authorisationService.userAllowedToViewGeoServiceLayer(geoService, terreindeelLayer));
  }
}

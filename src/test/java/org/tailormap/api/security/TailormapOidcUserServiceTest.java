/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TailormapOidcUserServiceTest {
  private static final List<String> ROLES = List.of("tailormap-viewer", "tailormap-admin", "other-app");

  @Test
  void keeps_all_roles_without_filter() {
    assertEquals(ROLES, TailormapOidcUserService.filterRoles(ROLES, null, "test"));
  }

  @Test
  void keeps_matching_roles() {
    assertEquals(
        List.of("tailormap-viewer", "tailormap-admin"),
        TailormapOidcUserService.filterRoles(ROLES, "^tailormap-", "test"));
  }

  @Test
  void keeps_no_roles_with_invalid_filter() {
    assertEquals(List.of(), TailormapOidcUserService.filterRoles(ROLES, "[tailormap", "test"));
  }
}

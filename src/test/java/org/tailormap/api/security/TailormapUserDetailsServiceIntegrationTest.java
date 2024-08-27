/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
class TailormapUserDetailsServiceIntegrationTest {

  @Autowired TailormapUserDetailsService userDetailsService;

  @Test
  void testLoadUserByUsernameUserDoesNotExist() {
    UsernameNotFoundException thrown =
        assertThrows(
            UsernameNotFoundException.class,
            () -> userDetailsService.loadUserByUsername("doesnotexist"),
            "UsernameNotFoundException was expected");
    assertNotNull(thrown, "thrown exception should not be null");
  }
}

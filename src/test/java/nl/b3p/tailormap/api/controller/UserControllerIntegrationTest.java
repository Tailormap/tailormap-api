/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired UserController userController;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnauthenticatedGetUser() throws Exception {
    assertNotNull(userController, "userController can not be `null` if Spring Boot works");

    mockMvc
        .perform(get(apiBasePath + "/user"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.isAuthenticated").value(false))
        .andExpect(jsonPath("$.username").isEmpty())
        .andExpect(jsonPath("$.roles").isEmpty());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void testAuthenticatedGetUser() throws Exception {
    assertNotNull(userController, "userController can not be `null` if Spring Boot works");

    mockMvc
        .perform(get(apiBasePath + "/user"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.isAuthenticated").value(true))
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.roles.length()").value(1))
        .andExpect(jsonPath("$.roles[0]").value(Group.ADMIN));
  }
}

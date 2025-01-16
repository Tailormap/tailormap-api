/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  UserController userController;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void testUnauthenticatedGetUser() throws Exception {
    assertNotNull(userController, "userController can not be `null` if Spring Boot works");

    mockMvc.perform(get(apiBasePath + "/user"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.isAuthenticated").value(false))
        .andExpect(jsonPath("$.username").isEmpty())
        .andExpect(jsonPath("$.roles").isEmpty())
        .andExpect(jsonPath("$.properties").isEmpty())
        .andExpect(jsonPath("$.groupProperties").isEmpty());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN, "test-bar", "test-baz"})
  void testAuthenticatedGetUser() throws Exception {
    assertNotNull(userController, "userController can not be `null` if Spring Boot works");

    mockMvc.perform(get(apiBasePath + "/user"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.isAuthenticated").value(true))
        .andExpect(jsonPath("$.username").value("tm-admin"))
        .andExpect(jsonPath("$.roles.length()").value(3))
        .andExpect(jsonPath("$.roles").value(Matchers.containsInAnyOrder(Group.ADMIN, "test-bar", "test-baz")))
        .andExpect(jsonPath("$.properties.length()").value(1))
        .andExpect(jsonPath("$.properties[0].key").value("some-property"))
        .andExpect(jsonPath("$.properties[0].value").value("some-value"))
        .andExpect(jsonPath("$.groupProperties.length()").value(1))
        .andExpect(jsonPath("$.groupProperties[0].key").value("group-property"))
        .andExpect(jsonPath("$.groupProperties[0].value").value(Boolean.TRUE));
  }
}

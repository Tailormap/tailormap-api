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

import nl.b3p.tailormap.api.JPAConfiguration;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Testcases for {@link UserController}. */
@SpringBootTest(
    classes = {
      JPAConfiguration.class,
      SecurityConfig.class,
      AuthorizationService.class,
      UserController.class,
    })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
class UserControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired UserController userController;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testUnauthenticatedGetUser() throws Exception {
    assertNotNull(userController, "userController can not be `null` if Spring Boot works");

    mockMvc
        .perform(get("/user"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.isAuthenticated").value(false))
        .andExpect(jsonPath("$.username").value(""));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void testAuthenticatedGetUser() throws Exception {
    assertNotNull(userController, "userController can not be `null` if Spring Boot works");

    mockMvc
        .perform(get("/user"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.isAuthenticated").value(true))
        .andExpect(jsonPath("$.username").value("admin"));
  }
}

/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import static nl.b3p.tailormap.api.TestRequestProcessor.setServletPath;
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

/**
 * Integration test for the API security configuration. The test indirectly verifies that the API
 * {@code app} endpoint is secured as expected.
 */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class ApiSecurityConfigurationIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void anonymousSecuredAppTest() throws Exception {
    String path = apiBasePath + "/app/secured";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @WithMockUser(
      username = "user",
      authorities = {Group.AUTHENTICATED})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void authenticatedSecuredAppTest() throws Exception {
    String path = apiBasePath + "/app/secured";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.kind").value("app"));
  }
}

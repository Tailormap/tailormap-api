/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@TestPropertySource(
    properties = {
      "tailormap-api.mail.from=test",
      "spring.mail.host=dummy",
      "spring.mail.port=25",
      "spring.mail.username=test",
      "spring.mail.password=test"
    })
class PasswordResetControllerIntegrationTest {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private MockMvc mockMvc;

  /**
   * Test the password reset request endpoint. This test only verifies that the endpoint returns a 200 OK response.
   * The actual sending of the email is not tested here as it happens in a separate thread.
   *
   * @throws Exception in case of errors
   */
  @Test
  void testRequestPasswordReset() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("username=foo")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Your password reset request is being processed"));
  }
}

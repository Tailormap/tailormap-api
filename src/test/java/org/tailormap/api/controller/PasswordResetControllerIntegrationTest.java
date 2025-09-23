/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.repository.TemporaryTokenRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class PasswordResetControllerIntegrationTest {
  private static final String MAIL_EMAIL = "tailormap@tailormap.com";
  private static final String MAIL_USER = "tailormap";
  private static final String MAIL_PASSWORD = "tailormap";

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
      .withConfiguration(GreenMailConfiguration.aConfig().withUser(MAIL_EMAIL, MAIL_USER, MAIL_PASSWORD));

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TemporaryTokenRepository temporaryTokenRepository;

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

    // we need some time here to wait for the email thread to complete
    Awaitility.await().atMost(5, SECONDS).until(() -> temporaryTokenRepository.findAll().stream()
        .anyMatch(token -> token.getUsername().equals("foo")));
    Awaitility.await().atMost(5, SECONDS).until(() -> greenMail.getReceivedMessages().length == 1);

    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertEquals(1, emails.length);
    assertEquals("Password reset request", emails[0].getSubject());
    assertEquals(1, emails[0].getAllRecipients().length);
    assertThat(emails[0].getAllRecipients()[0].toString(), containsString("foo@example.com"));
    assertThat((String) emails[0].getContent(), containsString("Your reset token url: http://localhost"));
  }

  @Test
  void testRequestPasswordResetDisabledForActuator() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("username=actuator")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Your password reset request is being processed"));
    Awaitility.await()
        .pollDelay(5, SECONDS)
        .untilAsserted(() -> assertEquals(
            0,
            temporaryTokenRepository.countByUsername("actuator"),
            "There should be no tokens for actuator's request"));
    Awaitility.await()
        .pollDelay(3, SECONDS)
        .untilAsserted(() -> assertEquals(
            0, greenMail.getReceivedMessages().length, "There should be no emails for actuator's request"));
  }
}

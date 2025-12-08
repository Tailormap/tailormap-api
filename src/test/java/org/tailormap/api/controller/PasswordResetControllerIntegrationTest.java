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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.TemporaryToken;
import org.tailormap.api.repository.TemporaryTokenRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
   * Test the Tailormap: password reset request endpoint. This test only verifies that the endpoint returns a 202
   * ACCEPTED response. The actual sending of the email is not tested here as it happens in a separate thread.
   *
   * @throws Exception in case of errors
   */
  @Test
  @Order(1)
  void request_password_reset_for_foo() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .param("email", "foo@example.com")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("Your password reset request is being processed"));

    // we need some time here to wait for the email thread to complete
    Awaitility.await().atMost(5, SECONDS).until(() -> temporaryTokenRepository.findAll().stream()
        .anyMatch(token -> token.getUsername().equals("foo")));
    Awaitility.await().atMost(5, SECONDS).until(() -> greenMail.getReceivedMessages().length == 1);

    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertEquals(1, emails.length);
    assertEquals("Tailormap: password reset request", emails[0].getSubject());
    assertEquals(1, emails[0].getAllRecipients().length);
    assertThat(emails[0].getAllRecipients()[0].toString(), containsString("foo@example.com"));
    assertThat(
        (String) emails[0].getContent(),
        containsString(
            "To reset your Tailormap password, click the following link: http://localhost/user/password-reset/"));
  }

  @Test
  @Order(2)
  void confirm_password_reset_for_foo() throws Exception {
    final String confirmUrl = apiBasePath + "/user/reset-password";
    final TemporaryToken latestToken = temporaryTokenRepository.findAll().stream()
        .reduce((first, second) -> second)
        .orElse(null);
    assertNotNull(latestToken, "There should be a token for user foo");

    mockMvc.perform(post(confirmUrl)
            .param("token", latestToken.getToken().toString())
            .param("newPassword", "8fd106f1-3408-43bd-a961-12b20079bb17")
            .param("username", "foo")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(confirmUrl))
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Your password reset was reset successful"));
  }

  @SuppressWarnings("UnusedMethod")
  private static Stream<Arguments> localisedStrings() {
    return Stream.of(
        Arguments.of(
            "nl",
            "Tailormap: verzoek om wachtwoord opnieuw in te stellen",
            "Om uw Tailormap-wachtwoord opnieuw in te stellen, klikt u op de volgende link: http://localhost/user/password-reset/"),
        Arguments.of(
            "de",
            "Tailormap: Anfrage zum Zurücksetzen des Passworts",
            "Um Ihr Tailormap-Passwort zurückzusetzen, klicken Sie auf den folgenden Link: http://localhost/user/password-reset/"),
        Arguments.of(
            "en",
            "Tailormap: password reset request",
            "To reset your Tailormap password, click the following link: http://localhost/user/password-reset/"),
        // fallback to default language, because we don't speak Spanish
        Arguments.of(
            "es",
            "Tailormap: password reset request",
            "To reset your Tailormap password, click the following link: http://localhost/user/password-reset/"));
  }

  @ParameterizedTest
  @MethodSource("localisedStrings")
  void request_password_reset_localised(String locale, String expectedSubject, String expectedBody) throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("email=foo@example.com")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .locale(Locale.of(locale))
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("Your password reset request is being processed"));

    // we need some time here to wait for the email thread to complete
    Awaitility.await().atMost(5, SECONDS).until(() -> temporaryTokenRepository.findAll().stream()
        .anyMatch(token -> token.getUsername().equals("foo")));
    Awaitility.await().atMost(5, SECONDS).until(() -> greenMail.getReceivedMessages().length == 1);

    final MimeMessage[] emails = greenMail.getReceivedMessages();
    assertEquals(1, emails.length);
    assertEquals(expectedSubject, emails[0].getSubject());
    assertEquals(1, emails[0].getAllRecipients().length);
    assertThat(emails[0].getAllRecipients()[0].toString(), containsString("foo@example.com"));
    assertThat(emails[0].getContent().toString(), containsString(expectedBody));
  }

  @Test
  void request_reset_password_invalid_mail() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("email=tm-admin")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("must be a well-formed email address")));

    Awaitility.await()
        .pollDelay(5, SECONDS)
        .untilAsserted(() -> assertEquals(
            0,
            temporaryTokenRepository.countByUsername("tm-admin"),
            "There should be no tokens for this request"));
  }

  @Test
  void request_reset_password_empty() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("email=")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("Your password reset request is being processed"));

    Awaitility.await()
        .pollDelay(3, SECONDS)
        .untilAsserted(() -> assertEquals(
            0, greenMail.getReceivedMessages().length, "There should be no emails this request"));
  }

  @Test
  void request_password_wrong_parameter() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("username=actuator")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void request_reset_password_expired_user() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("email=expired@example.com")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("Your password reset request is being processed"));
    Awaitility.await()
        .pollDelay(5, SECONDS)
        .untilAsserted(() -> assertEquals(
            0,
            temporaryTokenRepository.countByUsername("expired"),
            "There should be no tokens for this request"));
    Awaitility.await()
        .pollDelay(3, SECONDS)
        .untilAsserted(() -> assertEquals(
            0, greenMail.getReceivedMessages().length, "There should be no emails this request"));
  }

  @Test
  void request_reset_password_disabled_user() throws Exception {
    final String url = apiBasePath + "/password-reset";
    mockMvc.perform(post(url)
            .content("email=disabled@example.com")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .with(csrf()))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("Your password reset request is being processed"));
    Awaitility.await()
        .pollDelay(5, SECONDS)
        .untilAsserted(() -> assertEquals(
            0,
            temporaryTokenRepository.countByUsername("disabled"),
            "There should be no tokens for this request"));
    Awaitility.await()
        .pollDelay(3, SECONDS)
        .untilAsserted(() -> assertEquals(
            0, greenMail.getReceivedMessages().length, "There should be no emails this request"));
  }
}

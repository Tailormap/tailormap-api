/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tailormap.api.persistence.TemporaryToken;
import org.tailormap.api.repository.TemporaryTokenRepository;
import org.tailormap.api.repository.UserRepository;

@RestController
@Validated
public class PasswordResetController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final JavaMailSender emailSender;
  private final UserRepository userRepository;
  private final TemporaryTokenRepository temporaryTokenRepository;

  @Value("${tailormap-api.mail.from}")
  private String mailFrom;

  @Value("${tailormap-api.password-reset.enabled:true}")
  private boolean passwordResetEnabled;

  @Value("${tailormap-api.password-reset.disabled-for}")
  private Set<String> passwordResetDisabledFor;

  public PasswordResetController(
      JavaMailSender emailSender,
      UserRepository userRepository,
      TemporaryTokenRepository temporaryTokenRepository) {
    this.emailSender = emailSender;
    this.userRepository = userRepository;
    this.temporaryTokenRepository = temporaryTokenRepository;
  }

  /**
   * Request a password reset email. The email will be sent asynchronously.
   *
   * @param username the username to request a password reset for
   * @return 200 OK
   */
  @PostMapping(
      path = "${tailormap-api.base-path}/password-reset",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Counted(value = "tailormap_api_password_reset_request", description = "number password reset requests")
  public ResponseEntity<Serializable> requestPasswordReset(
      @RequestParam @Valid String username, HttpServletRequest request) {
    if (passwordResetEnabled && !passwordResetDisabledFor.contains(username)) {
      this.sendPasswordResetEmail(username, request);
    }

    return ResponseEntity.ok(
        new ObjectMapper().createObjectNode().put("message", "Your password reset request is being processed"));
  }

  private void sendPasswordResetEmail(String username, HttpServletRequest request) {
    try {
      // Build absolute URL considering proxy headers; we can't do this inside the email thread as it may throw an
      // IllegalStateException: The request object has been recycled and is no longer associated with this facade
      String scheme = request.getHeader("X-Forwarded-Proto") != null
          ? request.getHeader("X-Forwarded-Proto")
          : request.getScheme();
      String host = request.getHeader("X-Forwarded-Host") != null
          ? request.getHeader("X-Forwarded-Host")
          : request.getServerName();
      int port = request.getHeader("X-Forwarded-Port") != null
          ? Integer.parseInt(request.getHeader("X-Forwarded-Port"))
          : request.getServerPort();

      ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
      emailExecutor.execute(() -> {
        String email =
            this.userRepository.findById(username).orElseThrow().getEmail();
        TemporaryToken token = new TemporaryToken(TemporaryToken.TokenType.PASSWORD_RESET, username);
        token = temporaryTokenRepository.save(token);

        String absoluteLink = scheme + "://" + host
            + ((scheme.equals("http") && port != 80) || (scheme.equals("https") && port != 443)
                ? ":" + port
                : "")
            + linkTo(methodOn(UserController.class).getPasswordReset(token.getToken()));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Password reset request");
        message.setText("Your reset token url: %s".formatted(absoluteLink));

        logger.trace("Sending message {}", message);
        logger.info("Sending password reset email for user: {}", username);
        emailSender.send(message);
      });
      emailExecutor.shutdown();
    } catch (Exception e) {
      logger.error("Failed to send password reset email", e);
    }
  }
}

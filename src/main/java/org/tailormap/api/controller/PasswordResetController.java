/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import jakarta.validation.Valid;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
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

  @Value("${tailormap-api.base-path}")
  private String basePath;

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
      /* Can't use ${tailormap-api.base-path} because WebMvcLinkBuilder.linkTo() won't work */
      path = "/api/password-reset",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Counted(value = "tailormap_api_password_reset_request", description = "number password reset requests")
  public ResponseEntity<Serializable> requestPasswordReset(@RequestParam @Valid String username) {
    this.sendPasswordResetEmail(username);

    return ResponseEntity.ok(
        new ObjectMapper().createObjectNode().put("message", "Your password reset request is being processed"));
  }

  private void sendPasswordResetEmail(String username) {
    try {
      ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
      emailExecutor.execute(() -> {
        String email =
            this.userRepository.findById(username).orElseThrow().getEmail();
        TemporaryToken token = new TemporaryToken(username);
        token = temporaryTokenRepository.save(token);

        String link = linkTo(UserController.class)
            .slash(basePath)
            .slash("TODO_reset_password")
            .slash(token.getToken())
            .toString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("Password reset request");
        message.setText("Your reset token url: %s".formatted(link));
        emailSender.send(message);

        logger.debug("Sending message {}", message);
        logger.info("Sending password reset email for user: {}", username);
      });
      emailExecutor.shutdown();
    } catch (Exception e) {
      logger.error("Failed to send password reset email", e);
    }
  }
}

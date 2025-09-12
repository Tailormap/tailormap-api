/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tailormap.api.repository.UserRepository;

@RestController
@Validated
public class PasswordResetController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private JavaMailSender emailSender;
  private UserRepository userRepository;

  @Value("${tailormap-api.mail.from}")
  private String mailFrom;

  public PasswordResetController(JavaMailSender emailSender, UserRepository userRepository) {
    this.emailSender = emailSender;
    this.userRepository = userRepository;
  }

  @GetMapping(
      path = "${tailormap-api.base-path}/password-reset/{username}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> requestPasswordReset(String username) {
    this.sendPasswordResetEmail(username);
    // TODO implement password reset logic
    return ResponseEntity.ok().build();
  }

  private void sendPasswordResetEmail(String username) {
    // TODO Start a thread to:
    //  - lookup email by user
    //  - Generate token and save it with expiration
    //  - send email with token link
    ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
    emailExecutor.execute(
        () -> {
          try {
            String email = this.userRepository.findById(username).orElseThrow().getEmail();
            logger.info("Sending password reset email for user: {}", username);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(email);
            message.setSubject("subject");
            message.setText("text");
            emailSender.send(message);
          } catch (NoSuchElementException | MailException e) {
            logger.error("failed", e);
          }
        });
    emailExecutor.shutdown();
  }
}

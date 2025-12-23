/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.service;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.tailormap.api.persistence.TemporaryToken;
import org.tailormap.api.persistence.User;
import org.tailormap.api.repository.TemporaryTokenRepository;
import org.tailormap.api.repository.UserRepository;

@Service
public class PasswordResetEmailService {

  private static final Logger logger = LoggerFactory.getLogger(PasswordResetEmailService.class);

  private final JavaMailSender emailSender;
  private final UserRepository userRepository;
  private final TemporaryTokenRepository temporaryTokenRepository;
  private final MessageSource messageSource;

  @Value("${tailormap-api.mail.from}")
  private String mailFrom;

  public PasswordResetEmailService(
      JavaMailSender emailSender,
      UserRepository userRepository,
      TemporaryTokenRepository temporaryTokenRepository,
      MessageSource messageSource) {
    this.emailSender = emailSender;
    this.userRepository = userRepository;
    this.temporaryTokenRepository = temporaryTokenRepository;
    this.messageSource = messageSource;
  }

  @Async("passwordResetTaskExecutor")
  public void sendPasswordResetEmailAsync(
      String email, String absoluteLinkPrefix, Locale locale, int tokenExpiryMinutes) {
    try {
      User user = userRepository.findByEmail(email).orElse(null);
      if (user == null || !user.isEnabledAndValidUntil()) {
        return;
      }

      TemporaryToken token =
          new TemporaryToken(TemporaryToken.TokenType.PASSWORD_RESET, user.getUsername(), tokenExpiryMinutes);
      token = temporaryTokenRepository.save(token);

      String absoluteLink =
          absoluteLinkPrefix + "/user/password-reset/" + token.getCombinedTokenAndExpirationAsBase64();

      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(mailFrom);
      message.setTo(user.getEmail());
      message.setSubject(messageSource.getMessage("reset-password-request.email-subject", null, locale));
      message.setText(
          messageSource.getMessage("reset-password-request.email-body", new Object[] {absoluteLink}, locale));

      logger.info("Sending password reset email for user: {}", user.getUsername());
      emailSender.send(message); // blocking, but run in async thread
    } catch (Exception e) {
      logger.error("Failed to send password reset email", e);
    }
  }
}

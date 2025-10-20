/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.tailormap.api.util.TMPasswordDeserializer.encoder;
import static org.tailormap.api.util.TMPasswordDeserializer.validatePasswordStrength;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.LocaleResolver;
import org.tailormap.api.configuration.TailormapPasswordStrengthConfig;
import org.tailormap.api.persistence.TemporaryToken;
import org.tailormap.api.persistence.User;
import org.tailormap.api.repository.TemporaryTokenRepository;
import org.tailormap.api.repository.UserRepository;
import org.tailormap.api.viewer.model.ErrorResponse;

@RestController
@Validated
@ConditionalOnProperty(name = "tailormap-api.password-reset.enabled", havingValue = "true")
public class PasswordResetController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final JavaMailSender emailSender;
  private final UserRepository userRepository;
  private final TemporaryTokenRepository temporaryTokenRepository;
  private final MessageSource messageSource;
  private final LocaleResolver localeResolver;

  @Value("${tailormap-api.mail.from}")
  private String mailFrom;

  @Value("${tailormap-api.password-reset.enabled:false}")
  private boolean passwordResetEnabled;

  @Value("${tailormap-api.password-reset.disabled-for}")
  private Set<String> passwordResetDisabledFor;

  @Value("${tailormap-api.password-reset.token-expiration-minutes:5}")
  private int passwordResetTokenExpirationMinutes;

  public PasswordResetController(
      JavaMailSender emailSender,
      UserRepository userRepository,
      TemporaryTokenRepository temporaryTokenRepository,
      MessageSource messageSource,
      LocaleResolver localeResolver) {
    this.emailSender = emailSender;
    this.userRepository = userRepository;
    this.temporaryTokenRepository = temporaryTokenRepository;
    this.messageSource = messageSource;
    this.localeResolver = localeResolver;
  }

  @ExceptionHandler({ConstraintViolationException.class})
  public ResponseEntity<?> handleException(ConstraintViolationException e) {
    // wrap the exception in a proper json response
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse().message(e.getMessage()).code(HttpStatus.BAD_REQUEST.value()));
  }

  /**
   * Request a password reset email. The email will be sent asynchronously.
   *
   * @param email the email address to request a password reset for
   * @return 202 ACCEPTED with a message that the request is being processed
   * @throws ConstraintViolationException when the email is not valid
   */
  @PostMapping(
      path = "${tailormap-api.base-path}/password-reset",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Counted(value = "tailormap_api_password_reset_request", description = "number of password reset requests by email")
  public ResponseEntity<Serializable> requestPasswordReset(
      @RequestParam @Email String email, HttpServletRequest request) throws ConstraintViolationException {
    if (passwordResetEnabled && !passwordResetDisabledFor.contains(email)) {
      this.sendPasswordResetEmail(email, request);
    }

    return ResponseEntity.accepted()
        .body(new ObjectMapper()
            .createObjectNode()
            .put("message", "Your password reset request is being processed"));
  }

  @PostMapping(
      path = "${tailormap-api.base-path}/user/reset-password",
      produces = MediaType.APPLICATION_JSON_VALUE,
      consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  @Counted(
      value = "tailormap_api_password_reset_confirmation",
      description = "number of submitted password reset confirmations")
  @Transactional
  public ResponseEntity<Serializable> confirmPasswordReset(
      @NotNull UUID token, @NotNull String username, @NotNull String newPassword) throws ResponseStatusException {

    final TemporaryToken temporaryToken = temporaryTokenRepository
        .findById(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token invalid"));

    // check if password is valid
    boolean validation = TailormapPasswordStrengthConfig.getValidation();
    int minLength = TailormapPasswordStrengthConfig.getMinLength();
    int minStrength = TailormapPasswordStrengthConfig.getMinStrength();
    if (validation && !validatePasswordStrength(newPassword, minLength, minStrength)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password too short or too easily guessable");
    }

    // check if token is valid (not expired, correct type and username matches)
    if (temporaryToken.getExpirationTime().isAfter(Instant.now().atZone(ZoneId.of("UTC")))
        && temporaryToken.getTokenType() == TemporaryToken.TokenType.PASSWORD_RESET
        && temporaryToken.getUsername().equals(username)) {
      // only reset password and return an OK response if user exists, is enabled and account has not expired
      // even here we don't want to reveal if the user exists or not
      final User user = userRepository.findById(username).orElse(null);
      if (user != null && user.isEnabledAndValidUntil()) {

        userRepository.updatePassword(username, encoder().encode(newPassword));
        logger.info("Password reset successful for user: {}", user.getUsername());
        temporaryTokenRepository.delete(temporaryToken);
        return ResponseEntity.status(HttpStatus.OK)
            .body(new ObjectMapper()
                .createObjectNode()
                .put("message", "Your password reset was reset successful"));
      }
    }
    // if we reach this, something was wrong with the token or the user
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired or invalid request");
  }

  private void sendPasswordResetEmail(String email, HttpServletRequest request) {
    final String absoluteLinkPrefix =
        request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath());
    final Locale locale = localeResolver.resolveLocale(request);

    try (ExecutorService emailExecutor = Executors.newSingleThreadExecutor()) {
      emailExecutor.execute(() -> {
        try {
          this.userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEnabledAndValidUntil()) return;

            TemporaryToken token = new TemporaryToken(
                TemporaryToken.TokenType.PASSWORD_RESET,
                user.getUsername(),
                passwordResetTokenExpirationMinutes);
            token = temporaryTokenRepository.save(token);

            String absoluteLink = absoluteLinkPrefix
                + /* this is the route in the angular application */ "/user/password-reset/"
                + token.getCombinedTokenAndExpirationAsBase64();

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(user.getEmail());
            message.setSubject(
                messageSource.getMessage("reset-password-request.email-subject", null, locale));
            message.setText(messageSource.getMessage(
                "reset-password-request.email-body", new Object[] {absoluteLink}, locale));

            logger.trace("Sending message {}", message);
            logger.info("Sending password reset email for user: {}", user.getUsername());
            emailSender.send(message);
          });
        } catch (MailException e) {
          logger.error("Failed to send password reset email", e);
        } catch (Exception e) {
          logger.error("Unexpected exception in password reset email thread", e);
        }
      });
      emailExecutor.shutdown();
    } catch (RejectedExecutionException e) {
      logger.error("Failed to start password reset email thread", e);
    }
  }
}

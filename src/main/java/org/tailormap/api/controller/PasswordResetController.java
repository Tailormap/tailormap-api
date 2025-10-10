/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.annotation.Counted;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Email;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;
import org.tailormap.api.persistence.TemporaryToken;
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

  @Value("${tailormap-api.password-reset.enabled:true}")
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
  @Counted(value = "tailormap_api_password_reset_request", description = "number of password reset requests")
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

  private void sendPasswordResetEmail(String email, HttpServletRequest request) {

    final String absoluteLinkPrefix =
        request.getRequestURL().toString().replace(request.getRequestURI(), request.getContextPath());
    final Locale locale = localeResolver.resolveLocale(request);

    try {
      // XXX We may need to build the absolute outside the email thread as it may throw an IllegalStateException:
      // The request object has been recycled and is no longer associated with this facade
      ExecutorService emailExecutor = Executors.newSingleThreadExecutor();
      emailExecutor.execute(() -> {
        try {
          this.userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEnabled()
                || (user.getValidUntil() != null
                    && user.getValidUntil()
                        .isBefore(OffsetDateTime.now(ZoneId.systemDefault())
                            .toZonedDateTime()))) return;

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

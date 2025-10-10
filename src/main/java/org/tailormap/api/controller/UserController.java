/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.tailormap.api.util.TMPasswordDeserializer.encoder;
import static org.tailormap.api.util.TMPasswordDeserializer.validatePasswordStrength;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.configuration.TailormapPasswordStrengthConfig;
import org.tailormap.api.persistence.TemporaryToken;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.helper.UploadHelper;
import org.tailormap.api.repository.TemporaryTokenRepository;
import org.tailormap.api.repository.UserRepository;
import org.tailormap.api.security.OIDCRepository;
import org.tailormap.api.security.TailormapAdditionalProperty;
import org.tailormap.api.security.TailormapUserDetails;
import org.tailormap.api.viewer.model.AdditionalProperty;
import org.tailormap.api.viewer.model.LoginConfiguration;
import org.tailormap.api.viewer.model.LoginConfigurationSsoLinksInner;
import org.tailormap.api.viewer.model.UserResponse;

/** Provides user and login information */
@RestController
public class UserController {

  private final OIDCRepository oidcRepository;
  private final TemporaryTokenRepository temporaryTokenRepository;
  private final UserRepository userRepository;
  private final UploadHelper uploadHelper;

  @Value("${tailormap-api.password-reset.enabled:true}")
  private boolean passwordResetEnabled;

  public UserController(
      OIDCRepository oidcRepository,
      TemporaryTokenRepository temporaryTokenRepository,
      UserRepository userRepository,
      UploadHelper uploadHelper) {
    this.oidcRepository = oidcRepository;
    this.temporaryTokenRepository = temporaryTokenRepository;
    this.userRepository = userRepository;
    this.uploadHelper = uploadHelper;
  }

  /**
   * Get user login information.
   *
   * @return isAuthenticated, username, roles
   */
  @GetMapping(path = "${tailormap-api.base-path}/user", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> getUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

    UserResponse userResponse = new UserResponse().isAuthenticated(isAuthenticated);
    if (isAuthenticated) {
      userResponse.username(authentication.getName());
      userResponse.setRoles(authentication.getAuthorities().stream()
          .map(GrantedAuthority::getAuthority)
          .collect(Collectors.toSet()));

      if (authentication.getPrincipal() instanceof TailormapUserDetails userProperties) {
        userResponse.setOrganisation(userProperties.getOrganisation());

        // Public user and group properties are meant for a (modified) frontend to implement custom
        // logic depending on who's logged in. When used for authorization for something, the check
        // should also be performed server-side, possibly in an extra microservice.

        // Authentication may be external (OIDC), and a user may not exist in the Tailormap database.
        // For users from the Tailormap database, we support additional user properties. Only return
        // public ones to the frontend.

        // Group properties are also supported for OIDC logins with roles that map to groups in the
        // Tailormap database.

        Function<TailormapAdditionalProperty, AdditionalProperty> mapToPublicProperty =
            ap -> new AdditionalProperty().key(ap.key()).value(ap.value());
        userProperties.getAdditionalProperties().stream()
            .filter(TailormapAdditionalProperty::isPublic)
            .map(mapToPublicProperty)
            .forEach(userResponse::addPropertiesItem);

        userProperties.getAdditionalGroupProperties().stream()
            .filter(TailormapAdditionalProperty::isPublic)
            .map(mapToPublicProperty)
            .forEach(userResponse::addGroupPropertiesItem);
      }
    }
    return ResponseEntity.status(HttpStatus.OK).body(userResponse);
  }

  @GetMapping(path = "${tailormap-api.base-path}/login/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LoginConfiguration> getLoginConfiguration() {
    LoginConfiguration result = new LoginConfiguration();

    for (ClientRegistration reg : oidcRepository) {
      OIDCRepository.OIDCRegistrationMetadata metadata =
          oidcRepository.getMetadataForRegistrationId(reg.getRegistrationId());
      result.addSsoLinksItem(new LoginConfigurationSsoLinksInner()
          .name(reg.getClientName())
          .url("/api/oauth2/authorization/" + reg.getRegistrationId())
          .showForViewer(metadata.getShowForViewer())
          .image(uploadHelper.getUrlForImage(metadata.getImage(), Upload.CATEGORY_SSO_IMAGE)));
    }

    result.enablePasswordReset(passwordResetEnabled);

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  //  @ConditionalOnProperty(name = "tailormap-api.password-reset.enabled", havingValue = "true")
  @PostMapping(path = "${tailormap-api.base-path}/user/reset-password", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> confirmPasswordReset(
      @NotNull UUID token, @NotNull String username, @NotNull String newPassword) throws ResponseStatusException {
    temporaryTokenRepository.findById(token).ifPresent(temporaryToken -> {
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
        userRepository.findById(username).ifPresent(user -> {
          // only reset password if user is enabled and account has not expired
          if (user.isEnabled()
              && (user.getValidUntil() != null
                  && user.getValidUntil()
                      .isAfter(Instant.now().atZone(ZoneId.of("UTC"))))) {
            user.setPassword(encoder().encode(newPassword));
            userRepository.save(user);
          }
        });
        temporaryTokenRepository.delete(temporaryToken);
      } else {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired or invalid");
      }
    });
    return ResponseEntity.status(HttpStatus.OK)
        .body(new ObjectMapper().createObjectNode().put("message", "Your password reset was reset successful"));
  }
}

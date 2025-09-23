/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.helper.UploadHelper;
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
  private final UploadHelper uploadHelper;

  public UserController(OIDCRepository oidcRepository, UploadHelper uploadHelper) {
    this.oidcRepository = oidcRepository;
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
  public ResponseEntity<LoginConfiguration> getSSOEndpoints() {
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

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }

  @GetMapping(
      /* Can't use ${tailormap-api.base-path} because WebMvcLinkBuilder.linkTo() won't work */
      path = "/api/reset_password/{uuid}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> getPasswordReset(@NotNull @PathVariable UUID uuid) {
    // TODO lookup the token, check if valid, return something useful
    throw new UnsupportedOperationException("TODO: Not implemented yet");
  }
}

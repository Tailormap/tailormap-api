/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.io.Serializable;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.persistence.json.AdminAdditionalProperty;
import nl.b3p.tailormap.api.repository.GroupRepository;
import nl.b3p.tailormap.api.repository.UserRepository;
import nl.b3p.tailormap.api.security.OIDCRepository;
import nl.b3p.tailormap.api.viewer.model.AdditionalProperty;
import nl.b3p.tailormap.api.viewer.model.LoginConfiguration;
import nl.b3p.tailormap.api.viewer.model.LoginConfigurationSsoLinksInner;
import nl.b3p.tailormap.api.viewer.model.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides user and login information */
@RestController
public class UserController {
  private final OIDCRepository oidcRepository;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;

  public UserController(
      OIDCRepository oidcRepository,
      UserRepository userRepository,
      GroupRepository groupRepository) {
    this.oidcRepository = oidcRepository;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
  }

  /**
   * Get user login information.
   *
   * @return isAuthenticated, username, roles
   */
  @GetMapping(path = "${tailormap-api.base-path}/user", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> getUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean isAuthenticated =
        authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

    UserResponse userResponse = new UserResponse().isAuthenticated(isAuthenticated);
    if (isAuthenticated) {
      userResponse.username(authentication.getName());
      userResponse.setRoles(
          authentication.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .collect(Collectors.toSet()));

      // Public user and group properties are meant for a (modified) frontend to implement custom
      // logic depending on who's logged in. When used for authorization to something the check
      // should also be performed server-side, possibly in an extra microservice.

      // Authentication may be external (OIDC) and a user may not exist in the Tailormap database,
      // but for users which are from the Tailormap database we support additional properties. Only
      // return public ones to the frontend.
      Function<AdminAdditionalProperty, AdditionalProperty> mapToPublicProperty =
          ap -> new AdditionalProperty().key(ap.getKey()).value(ap.getValue());
      userRepository
          .findById(authentication.getName())
          .map(User::getAdditionalProperties)
          .ifPresent(
              properties ->
                  properties.stream()
                      .filter(AdminAdditionalProperty::getIsPublic)
                      .map(mapToPublicProperty)
                      .forEach(userResponse::addPropertiesItem));

      // Even an externally authenticated user may have authorities which map to Groups from the
      // Tailormap database and have public properties.
      groupRepository.findAllById(userResponse.getRoles()).stream()
          .map(Group::getAdditionalProperties)
          .filter(Objects::nonNull)
          .flatMap(Collection::stream)
          .filter(AdminAdditionalProperty::getIsPublic)
          .map(mapToPublicProperty)
          .forEach(userResponse::addGroupPropertiesItem);
    }
    return ResponseEntity.status(HttpStatus.OK).body(userResponse);
  }

  @GetMapping(
      path = "${tailormap-api.base-path}/login/configuration",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LoginConfiguration> getSSOEndpoints() {
    LoginConfiguration result = new LoginConfiguration();

    for (ClientRegistration reg : oidcRepository) {
      OIDCRepository.OIDCRegistrationMetadata metadata =
          oidcRepository.getMetadataForRegistrationId(reg.getRegistrationId());
      result.addSsoLinksItem(
          new LoginConfigurationSsoLinksInner()
              .name(reg.getClientName())
              .url("/api/oauth2/authorization/" + reg.getRegistrationId())
              .showForViewer(metadata.getShowForViewer()));
    }

    return ResponseEntity.status(HttpStatus.OK).body(result);
  }
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.io.Serializable;
import java.util.stream.Collectors;
import nl.b3p.tailormap.api.viewer.model.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides user and login information */
@RestController
public class UserController {

  public UserController() {}

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
    }
    return ResponseEntity.status(HttpStatus.OK).body(userResponse);
  }
}

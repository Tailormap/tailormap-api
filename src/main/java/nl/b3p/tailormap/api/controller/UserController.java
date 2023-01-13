/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.model.UserResponse;
import nl.b3p.tailormap.api.security.TailormapUserDetailsService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;

/**
 * Provides user and login information
 *
 * @since 0.1
 */
@RestController
@CrossOrigin
public class UserController {

    public UserController() {}

    /**
     * Get user login information
     *
     * @return isAuthenticated, username
     */
    @GetMapping(path = "/user", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Serializable> getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isNotAuthenticated =
                authentication == null || authentication instanceof AnonymousAuthenticationToken;
        String username = isNotAuthenticated || authentication.getName() == null
                ? ""
                : authentication.getName();
        UserResponse userResponse =
                new UserResponse().isAuthenticated(!isNotAuthenticated).username(username);
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }
}

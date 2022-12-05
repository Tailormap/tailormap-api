/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.repository.ApplicationLayerRepository;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(annotations = AppRestController.class)
public class AppRestControllerAdvice {

    private final ApplicationRepository applicationRepository;
    private final ApplicationLayerRepository applicationLayerRepository;
    private final AuthorizationService authorizationService;

    public AppRestControllerAdvice(
            ApplicationRepository applicationRepository,
            ApplicationLayerRepository applicationLayerRepository,
            AuthorizationService authorizationService) {
        this.applicationRepository = applicationRepository;
        this.applicationLayerRepository = applicationLayerRepository;
        this.authorizationService = authorizationService;
    }

    @InitBinder
    protected void initBinder(WebDataBinder binder) {
        binder.setAllowedFields("appId", "appLayerId");
    }

    @ExceptionHandler(ResponseStatusException.class)
    protected ResponseEntity<?> handleResponseStatusException(ResponseStatusException ex) {
        if (HttpStatus.UNAUTHORIZED.equals(ex.getStatus())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RedirectResponse());
        }
        return ResponseEntity.status(ex.getRawStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse().message(ex.getReason()).code(ex.getRawStatusCode()));
    }

    @ModelAttribute
    public Application populateApplication(@PathVariable(required = false) Long appId) {
        if (appId == null) {
            // No binding for AppController which will return the default application instead of 404
            return null;
        }
        final Application application = applicationRepository.findById(appId).orElse(null);
        if (application == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Application with id " + appId + " not found");
        }
        if (!this.authorizationService.mayUserRead(application)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return application;
    }

    @ModelAttribute
    public ApplicationLayer populateApplicationLayer(
            @ModelAttribute Application application,
            @PathVariable(required = false) Long appLayerId) {
        if (appLayerId == null) {
            // No binding
            return null;
        }

        final ApplicationLayer applicationLayer =
                applicationLayerRepository.findById(appLayerId).orElse(null);
        if (applicationLayer == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Application layer with id " + appLayerId + " not found");
        }

        // Also verifies that the application layer actually belongs to the application
        if (!this.authorizationService.mayUserRead(applicationLayer, application)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return applicationLayer;
    }
}

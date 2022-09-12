/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import io.micrometer.core.annotation.Timed;

import nl.b3p.tailormap.api.exception.TailormapConfigurationException;
import nl.b3p.tailormap.api.model.AppResponse;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.model.RedirectResponse;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.MetadataRepository;
import nl.b3p.tailormap.api.security.AuthUtil;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import javax.persistence.EntityNotFoundException;

@RestController
@CrossOrigin
@RequestMapping(path = "/app")
public class AppController {
    private final Log logger = LogFactory.getLog(getClass());

    private final ApplicationRepository applicationRepository;
    private final MetadataRepository metadataRepository;

    @Value("${tailormap-api.apiVersion}")
    private String apiVersion;

    public AppController(
            ApplicationRepository applicationRepository, MetadataRepository metadataRepository) {
        this.applicationRepository = applicationRepository;
        this.metadataRepository = metadataRepository;
    }

    /**
     * Handle any {@code TailormapConfigurationException} that this controller might throw while
     * getting the application.
     *
     * @param exception the exception
     * @return an error response
     */
    @ExceptionHandler(TailormapConfigurationException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ErrorResponse handleTailormapConfigurationException(
            TailormapConfigurationException exception) {
        logger.fatal(exception.getMessage());
        return new ErrorResponse()
                .message("Internal server error. " + exception.getMessage())
                .code(500);
    }

    /**
     * Lookup an {@linkplain Application} with given parameters. Use this endpoint to get the id of
     * the requested or default application. Either call this with `name` and optional `version` or
     * `appId` alone. Will return general setup information such as name, appId, language, but not
     * map specific information, may return a redirect response for login.
     *
     * @param appId the unique identifier of an app
     * @param name the name of an app
     * @param version the version of an app
     * @return the basic information needed to create an app in the frontend
     * @since 0.1
     * @throws TailormapConfigurationException when the tailormap configuration is broken
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed(value = "get_app", description = "time spent to find an app")
    public ResponseEntity<Serializable> get(
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String version)
            throws TailormapConfigurationException {

        logger.trace(
                "requested application using: appId: "
                        + appId
                        + ", name: "
                        + name
                        + ", version: "
                        + version);

        Application application;
        if (null != appId) {
            application = applicationRepository.findById(appId).orElse(null);
        } else {
            application = findApplication(name, version);
        }

        if (null == application) {
            application = getDefaultViewer();
        }

        if (null == application) {
            // no default application or something else is very wrong
            throw new TailormapConfigurationException(
                    "Error getting the requested or default application.");
        } else if (application.isAuthenticatedRequired() && !AuthUtil.isAuthenticatedUser()) {
            // login required, send RedirectResponse
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new RedirectResponse());
        } else {
            logger.trace(
                    "found application - id:"
                            + application.getId()
                            + ", name: "
                            + application.getName()
                            + ", version: "
                            + application.getVersion()
                            + ", lang: "
                            + application.getLang()
                            + ", title: "
                            + application.getTitle());

            AppResponse appResponse =
                    new AppResponse()
                            .apiVersion(this.apiVersion)
                            .id(application.getId())
                            .name(application.getName())
                            // any of these 2 below + language could be null
                            .version(application.getVersion())
                            .title(application.getTitle());

            // null check language because it's an enumerated value
            if (null != application.getLang())
                appResponse.lang(AppResponse.LangEnum.fromValue(application.getLang()));

            return ResponseEntity.ok(appResponse);
        }
    }

    /**
     * recursive method to find application by name and version.
     *
     * @param name user given name
     * @param version user given version (can be {@code null})
     * @return found Application (or {@code null} if nothing found)
     */
    private Application findApplication(String name, String version) {
        Application application = null;
        if (name != null) {
            if (null != version) {
                application = applicationRepository.findByNameAndVersion(name, version);
            } else {
                application =
                        applicationRepository.findByName(name).stream()
                                .sorted()
                                .findFirst()
                                .orElse(null);
            }

            if (null == application) {
                logger.warn("No application found with name " + name + " and version " + version);
                String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
                if (!decodedName.equals(name)) {
                    return findApplication(decodedName, version);
                }
            }
        }
        return application;
    }

    /**
     * find the default viewer (name and version) in this instance.
     *
     * @return either the default Application or {@code null} if not configured
     */
    private Application getDefaultViewer() {
        try {
            Metadata md = metadataRepository.findByConfigKey(Metadata.DEFAULT_APPLICATION);
            String appId = md.getConfigValue();
            Long id = Long.parseLong(appId);
            return applicationRepository.getReferenceById(id);
        } catch (NullPointerException | EntityNotFoundException e) {
            logger.warn("No default application configured. " + e.getMessage());
            return null;
        }
    }
}

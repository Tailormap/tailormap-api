/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.exception.TailormapConfigurationException;
import nl.b3p.tailormap.api.model.ErrorResponse;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.MetadataRepository;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityNotFoundException;

@RestController
@CrossOrigin
@RequestMapping(path = "/app")
public class AppController {
    private final Log logger = LogFactory.getLog(getClass());

    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private MetadataRepository metadataRepository;

    @Value("${tailormap-api.apiVersion}")
    private String apiVersion;

    private Application application;

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
     * map specific information.
     *
     * @param appId the unique identifier of an app
     * @param name the name of an app
     * @param version the version of an app
     * @return the basic information needed to create an app in the frontend
     * @since 0.1
     * @throws TailormapConfigurationException when the tailormap configuration is broken
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> get(
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

        if (null != appId) {
            this.application = applicationRepository.findById(appId).orElse(null);
        } else {
            this.application = findApplication(name, version);
        }

        if (null == this.application) {
            this.application = getDefaultViewer();
        }

        if (null == this.application) {
            // no default application or something else is very wrong
            throw new TailormapConfigurationException(
                    "Error getting the requested or default application.");
        } else {
            logger.trace(
                    "found application - id:"
                            + this.application.getId()
                            + ", name: "
                            + this.application.getName()
                            + ", version: "
                            + this.application.getVersion()
                            + ", lang: "
                            + this.application.getLang()
                            + ", title: "
                            + this.application.getTitle());

            Map<String, Object> response =
                    new HashMap<>(
                            Map.of(
                                    // none of these should ever be null
                                    "apiVersion",
                                    this.apiVersion,
                                    "id",
                                    this.application.getId(),
                                    "name",
                                    this.application.getName()));
            // any of these could be null
            response.put("version", this.application.getVersion());
            response.put("lang", this.application.getLang());
            response.put("title", this.application.getTitle());

            return response;
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
     * @return either the default Application of {@code null} is not configured
     */
    private Application getDefaultViewer() {
        try {
            Metadata md = metadataRepository.findByConfigKey(Metadata.DEFAULT_APPLICATION);
            String appId = md.getConfigValue();
            Long id = Long.parseLong(appId);
            return applicationRepository.getById(id);
        } catch (NullPointerException | EntityNotFoundException e) {
            logger.warn("No default application configured. " + e.getMessage());
            return null;
        }
    }
}

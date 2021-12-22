/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.MetadataRepository;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Lookup an {@linkplain Application} with given parameters. Use this endpoint to get the id of
     * the requested or default application. Either call this with `name` and optional `version` or
     * `appid` alone. Will return general setup information such as name, appid, language, but not
     * map specific information.
     *
     * @param appid the unique identifier of an app
     * @param name the name of an app
     * @param version the version of an app
     * @return the basic information needed to create an app in the frontend
     * @since 0.1
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> get(
            @RequestParam(required = false) Long appid,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String version) {

        logger.trace(
                "requested application using: appid: "
                        + appid
                        + ", name: "
                        + name
                        + ", version: "
                        + version);

        if (null != appid) {
            Optional<Application> a = applicationRepository.findById(appid);
            this.application = a.isPresent() ? a.get() : null;
        } else {
            this.application = findApplication(name, version);
        }

        if (null == this.application) {
            this.application = getDefaultViewer();
        }

        if (null == this.application) {
            logger.fatal("Error getting the requested or default application.");
            return Map.of("code", 500, "message", "Internal server error");
        } else {
            return Map.of(
                    "apiVersion",
                    this.apiVersion,
                    "id",
                    this.application.getId(),
                    "name",
                    this.application.getName(),
                    "version",
                    this.application.getVersion(),
                    "lang",
                    this.application.getLang(),
                    "title",
                    this.application.getTitle()
                    // Note you can only have 10 K/V pairs in this Map.of(...)
                    );
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
                List<Application> applications = applicationRepository.findByName(name);
                Optional<Application> a = applications.stream().sorted().findFirst();
                application = a.isPresent() ? a.get() : null;
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

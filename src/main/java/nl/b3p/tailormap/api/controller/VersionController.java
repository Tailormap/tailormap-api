/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.repository.MetadataRepository;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides version information of backend, API and config database.
 *
 * @since 0.1
 */
@RestController
@CrossOrigin
public class VersionController {
    @Autowired private MetadataRepository metadataRepository;

    // Maven 'process-resources' takes care of updating these in the application.properties
    @Value("${tailormap-api.version}")
    private String version;

    @Value("${tailormap-api.api_version}")
    private String apiVersion;

    /**
     * get API version.
     *
     * @return api version
     */
    @GetMapping(path = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public String getVersion() {
        final Metadata m = metadataRepository.findByConfigKey(Metadata.DATABASE_VERSION_KEY);
        final String dbVersion = ((null != m) ? m.getConfigValue() : "-1");

        return String.format(
                "{\"version\":\"%s\", \"databaseversion\":\"%s\", \"api_version\":\"%s\"}",
                this.version, dbVersion, this.apiVersion);
    }
}

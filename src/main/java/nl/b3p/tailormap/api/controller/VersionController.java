/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.util.Map;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.tailormap.viewer.config.metadata.Metadata;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
  private final Log logger = LogFactory.getLog(getClass());
  private final ConfigurationRepository configurationRepository;

  // Maven 'process-resources' takes care of updating these tokens in the application.properties
  @Value("${tailormap-api.version}")
  private String version;

  @Value("${tailormap-api.apiVersion}")
  private String apiVersion;

  @Value("${tailormap-api.commitSha}")
  private String commitSha;

  @Value("${tailormap-api.builddate}")
  private String buildDate;

  public VersionController(ConfigurationRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
  }

  /**
   * get API version.
   *
   * @return api version
   */
  @GetMapping(path = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, String> getVersion() {
    final Metadata m = configurationRepository.findByConfigKey(Metadata.DATABASE_VERSION_KEY);
    final String dbVersion =
        null != m ? null == m.getConfigValue() ? "unknown" : m.getConfigValue() : "unknown";

    logger.debug(
        String.format(
            "Version information:\n\tproduct version: %s\n\tdatabase version: %s\n\tAPI version: %s\n\tgit commit: %s\n\tbuild date: %s",
            this.version, dbVersion, this.apiVersion, this.commitSha, this.buildDate));

    return Map.of(
        "version",
        this.version,
        "databaseversion",
        dbVersion,
        "apiVersion",
        this.apiVersion,
        "commitSha",
        this.commitSha,
        "buildDate",
        this.buildDate);
  }
}

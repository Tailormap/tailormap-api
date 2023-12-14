/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import java.io.Serializable;
import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import nl.b3p.tailormap.api.viewer.model.ConfigResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/** Provides global configuration values which are available to viewers */
@AppRestController
public class ConfigurationController {

  private final ConfigurationRepository configurationRepository;

  public ConfigurationController(ConfigurationRepository configurationRepository) {
    this.configurationRepository = configurationRepository;
  }

  @GetMapping(
      path = "${tailormap-api.base-path}/config/{key}",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Serializable> getConfig(@PathVariable String key) {
    Configuration config =
        configurationRepository
            .getAvailableForViewer(key)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

    ConfigResponse response = new ConfigResponse();
    response.setKey(key);
    response.setValue(config.getValue());
    response.setObject(config.getJsonValue());
    return ResponseEntity.status(HttpStatus.OK).body(response);
  }
}

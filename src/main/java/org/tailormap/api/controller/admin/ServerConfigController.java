/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import jakarta.servlet.MultipartConfigElement;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for getting some config properties for admins without exposing all configuration properties via the
 * Actuator configprops endpoint, which can include passwords and other confidential values.
 */
@RestController
public class ServerConfigController {
  private final MultipartConfigElement multipartConfigElement;

  public ServerConfigController(MultipartConfigElement multipartConfigElement) {
    this.multipartConfigElement = multipartConfigElement;
  }

  @GetMapping(path = "${tailormap-api.admin.base-path}/server/config")
  public ResponseEntity<Map<String, Object>> get() {
    // For showing the max file size for uploads in the admin
    return ResponseEntity.ok(Map.of("multipart", multipartConfigElement));
  }
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tailormap.api.configuration.TailormapPasswordStrengthConfig;
import org.tailormap.api.util.TMPasswordDeserializer;

@RestController
public class PasswordValidationController {
  @PostMapping(path = "${tailormap-api.admin.base-path}/validate-password")
  public ResponseEntity<Serializable> test(@RequestParam String password) {
    int minLength = TailormapPasswordStrengthConfig.getMinLength();
    int minStrength = TailormapPasswordStrengthConfig.getMinStrength();
    boolean result =
        TMPasswordDeserializer.validatePasswordStrength(password, minLength, minStrength);
    return ResponseEntity.ok(new ObjectMapper().createObjectNode().put("result", result));
  }
}

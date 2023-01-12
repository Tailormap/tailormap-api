/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.AppRestController;
import nl.b3p.tailormap.api.model.LayerExportCapabilities;
import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.Serializable;
import java.util.Collections;

@AppRestController
@Validated
@RequestMapping(path = "/app/{appId}/layer/{appLayerId}/export/")
public class LayerExportController {
    @GetMapping(path = "capabilities")
    public ResponseEntity<Serializable> capabilities(
            @ModelAttribute Application application,
            @ModelAttribute ApplicationLayer applicationLayer) {
        LayerExportCapabilities capabilities = new LayerExportCapabilities();
        capabilities.setExportable(false);
        capabilities.setOutputFormats(Collections.emptyList());
        return ResponseEntity.status(HttpStatus.OK).body(capabilities);
    }
}

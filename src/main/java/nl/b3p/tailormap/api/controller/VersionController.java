/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides version information. */
@RestController
public class VersionController {

    // the maven build takes care of updating this in the application.properties
    @Value("${tailormap-api.version}")
    private String version;

    /**
     * get API version.
     *
     * @return api version
     */
    @GetMapping(path = "/version")
    public String getVersion() {
        return this.version;
    }
}

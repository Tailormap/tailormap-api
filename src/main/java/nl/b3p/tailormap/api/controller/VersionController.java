/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** hardcoded version controller */
@RestController
public class VersionController {
    /**
     * get API version
     *
     * @return api version
     */
    @GetMapping(path = "/version")
    public String getVersion() {
        // TODO lookup version in eg. jar manifest or other resource
        return "0.1";
    }
}

/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@CrossOrigin
@RequestMapping(path = "/app")
public class AppController {
    private final Log logger = LogFactory.getLog(getClass());

    @Value("${tailormap-api.api_version}")
    private String apiVersion;

    /**
     * Produce version information.
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
            @RequestParam(required = false) Integer appid,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String version) {

        logger.trace("got: " + appid + ", name: " + name + ", version: " + version);

        // TODO implement, for now just echo
        return Map.of(
                "id",
                appid,
                "name",
                name,
                "apiVersion",
                this.apiVersion,
                "version",
                version,
                "title",
                "This is could be a cool mapping app",
                "lang",
                "nl_NL"
                // Note you can only have 10 K/V pairs in this Map.of(...)
                );
    }
}

/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin
@RequestMapping(path = "/app")
public class AppController {

    @Value("${tailormap-api.api_version}")
    private String apiVersion;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public String get(
            @RequestParam(required = false) Integer appid,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String version) {

        // TODO implement, for now just echo
        return "{\"id\": "
                + appid
                + ","
                + "\"api_version\": \""
                + apiVersion
                + "\","
                + "\"name\": \""
                + name
                + "\","
                + "\"version\": \""
                + version
                + "\","
                + "\"title\": \"This is could be a cool mapping app\","
                + "\"lang\": \"nl_NL\"}";
    }
}

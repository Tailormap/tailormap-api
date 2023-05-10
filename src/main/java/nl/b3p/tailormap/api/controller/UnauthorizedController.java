/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.viewer.model.UnauthorizedResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UnauthorizedController {
  @GetMapping("/api/unauthorized")
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public UnauthorizedResponse unauthorized() {
    return new UnauthorizedResponse().formLogin(true);
  }
}

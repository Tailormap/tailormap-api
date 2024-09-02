/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

import io.micrometer.core.annotation.Counted;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tailormap.api.viewer.model.UnauthorizedResponse;

@RestController
public class UnauthorizedController {
  @GetMapping("/api/unauthorized")
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  @Counted(value = "unauthorized", description = "Count of unauthorized requests")
  public UnauthorizedResponse unauthorized() {
    return new UnauthorizedResponse().unauthorized(true);
  }
}

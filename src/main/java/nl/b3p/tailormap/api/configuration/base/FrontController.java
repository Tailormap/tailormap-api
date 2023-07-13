/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.base;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Front controller which forwards requests to URLs created by the Angular routing module to the
 * proper viewer or admin index.html of the Angular bundle, for example when the user refreshes the
 * page. Unfortunately, a proper front controller would first check if the file which the requested
 * URL maps to exists and only return the index.html file if it doesn't. That is quite complicated
 * to do in Spring Boot so as a poor-mans alternative we explicitly match the specific router URLs
 * navigated to by the Angular frontends which don't map to files from the bundle.
 *
 * <p>It could happen that a frontend developer adds a route and forgets to update this front
 * controller, and it is only noticed later when someone presses F5 and gets a 404.
 */
@Controller
public class FrontController {
  @GetMapping(value = {"/login", "/app/**", "/service/**"})
  public String appIndex() {
    return "forward:/index.html";
  }

  @GetMapping(
      value = {
        "/admin",
        "/admin/login",
        "/admin/catalog",
        "/admin/catalog/**", // Extra line needed in order to match slashes. Regexps won't match.
        "/admin/users",
        "/admin/users/user/**",
        "/admin/groups",
        "/admin/groups/group/**",
        "/admin/applications",
        "/admin/applications/**",
        "/admin/oidc-configurations",
        "/admin/oidc-configurations/**"
      })
  public String adminIndex() {
    return "forward:/admin/index.html";
  }

  @GetMapping(value = {"/swagger-ui", "/swagger-ui/"})
  public String swaggerUiWelcomePage() {
    return "redirect:/swagger-ui/index.html";
  }
}

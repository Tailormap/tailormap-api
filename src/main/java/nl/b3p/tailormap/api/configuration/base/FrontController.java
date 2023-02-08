/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.base;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class FrontController {
  @RequestMapping(value = "/app/**")
  public String appIndex() {
    // Return static /index.html for URLs made the by Angular frontend app routing module which all
    // start with "/app/".
    return "forward:/index.html";
  }

  @RequestMapping(value = {"/swagger-ui", "/swagger-ui/"})
  public String swaggerUiWelcomePage() {
    return "forward:/swagger-ui/index.html";
  }
}

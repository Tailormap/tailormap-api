/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.base;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

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
  // Hardcoded list for now. In the future scan the spring.web.resources.static-locations directory
  // for subdirectories of locale-specific frontend bundles.
  private static final AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
  private static final String DEFAULT_LOCALE;

  static {
    localeResolver.setSupportedLocales(List.of(new Locale("en"), new Locale("nl")));
    localeResolver.setDefaultLocale(localeResolver.getSupportedLocales().get(0));
    DEFAULT_LOCALE = Objects.requireNonNull(localeResolver.getDefaultLocale()).toLanguageTag();
  }

  @GetMapping(value = {"/", "/login", "/app/**", "/service/**", "/admin/**"})
  public String appIndex(HttpServletRequest request) {
    Locale locale = localeResolver.resolveLocale(request);
    return "forward:/" + locale.toLanguageTag() + "/index.html";
  }

  @GetMapping(
      value = {
        "/{locale}/",
        "/{locale}/login",
        // Need to avoid matching /api/app/ etc
        "/{locale:^(?!api)[a-zA-Z-]+}/app/**",
        "/{locale:^(?!api)[a-zA-Z-]+}/service/**",
        "/{locale:^(?!api)[a-zA-Z-]+}/admin/**"
      })
  public String localePrefixedAppIndex(@PathVariable("locale") String locale, HttpServletRequest request) {
    if (localeResolver.getSupportedLocales().stream()
        .anyMatch(l -> l.toLanguageTag().equals(locale))) {
      return "forward:/" + locale + "/index.html";
    }
    return appIndex(request);
  }

  @GetMapping(value = {"/swagger-ui", "/swagger-ui/"})
  public String swaggerUiWelcomePage() {
    return "redirect:/swagger-ui/index.html";
  }
}

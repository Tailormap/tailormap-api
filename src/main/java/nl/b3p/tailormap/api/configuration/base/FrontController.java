/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration.base;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.util.UriUtils;

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

  static {
    localeResolver.setSupportedLocales(
        List.of(new Locale("en"), new Locale("nl"), new Locale("de")));
    localeResolver.setDefaultLocale(localeResolver.getSupportedLocales().get(0));
  }

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  public FrontController(
      @Lazy ConfigurationRepository configurationRepository,
      @Lazy ApplicationRepository applicationRepository) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
  }

  @GetMapping(
      value = {"/", "/login", "/app", "/app/", "/app/**", "/service/**", "/admin/**", "/ext/**"})
  public String appIndex(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    Application app = null;

    if (!activeProfile.contains("static-only")) {
      // The language setting of the (default) app takes precedence over the Accept-Language header
      if ("/".equals(path) || "/app".equals(path) || "/app/".equals(path)) {
        String defaultAppName = configurationRepository.get(Configuration.DEFAULT_APP);
        app = applicationRepository.findByName(defaultAppName);
      } else if (path.startsWith("/app/")) {
        String[] parts = path.split("/");
        if (parts.length > 2) {
          String appName = UriUtils.decode(parts[2], StandardCharsets.UTF_8);
          app = applicationRepository.findByName(appName);
        }
      }
    }

    if (app != null && app.getSettings().getI18nSettings() != null) {
      String appLanguage = app.getSettings().getI18nSettings().getDefaultLanguage();
      if (appLanguage != null
          && localeResolver.getSupportedLocales().stream()
              .anyMatch(l -> l.toLanguageTag().equals(appLanguage))) {
        return "forward:/" + appLanguage + "/index.html";
      }
    }

    // Resolve using Accept-Language header
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
        "/{locale:^(?!api)[a-zA-Z-]+}/admin/**",
        "/{locale:^(?!api)[a-zA-Z-]+}/ext/**"
      })
  public String localePrefixedAppIndex(
      @PathVariable("locale") String locale, HttpServletRequest request) {
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

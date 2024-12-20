/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration.base;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.springframework.web.util.UriUtils;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.ConfigurationRepository;

/**
 * Resolver which returns index.html for requests to paths created by the Angular routing module.
 * <br>
 * When the user refreshes the page such routes are requested from the server.
 */
public class FrontControllerResolver implements ResourceResolver {
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
  private final boolean staticOnly;

  public FrontControllerResolver(
      ConfigurationRepository configurationRepository,
      ApplicationRepository applicationRepository,
      boolean staticOnly) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
    this.staticOnly = staticOnly;
  }

  @Override
  public Resource resolveResource(
      HttpServletRequest request,
      @NonNull String requestPath,
      @NonNull List<? extends Resource> locations,
      ResourceResolverChain chain) {
    // Front controller logic: when routes used by the frontend are directly requested, return the
    // index.html instead of a 404.
    // Paths in @RequestMapping have higher priority than this resolver

    // When the resource exists (such as HTML, CSS, JS, etc.), return it
    Resource resource = chain.resolveResource(request, requestPath, locations);
    if (resource != null) {
      return resource;
    }

    // Check if the request path already starts with a locale prefix like en/ or nl/
    if (requestPath.matches("^[a-z]{2}/.*")) {
      return chain.resolveResource(request, requestPath.substring(0, 2) + "/index.html", locations);
    }

    // When the request path denotes an app, return the index.html for the default language
    // configured for the app

    if (!staticOnly) {
      Application app = null;
      if ("index.html".equals(requestPath) || requestPath.matches("^app/?")) {
        String defaultAppName = configurationRepository.get(Configuration.DEFAULT_APP);
        app = applicationRepository.findByName(defaultAppName);
      } else if (requestPath.startsWith("app/")) {
        String[] parts = requestPath.split("/", -1);
        if (parts.length > 1) {
          String appName = UriUtils.decode(parts[1], StandardCharsets.UTF_8);
          app = applicationRepository.findByName(appName);
        }
      }

      if (app != null && app.getSettings().getI18nSettings() != null) {
        String appLanguage = app.getSettings().getI18nSettings().getDefaultLanguage();
        if (appLanguage != null
            && localeResolver.getSupportedLocales().stream()
                .anyMatch(l -> l.toLanguageTag().equals(appLanguage))) {
          return chain.resolveResource(request, appLanguage + "/index.html", locations);
        }
      }
    }

    // Otherwise use the LocaleResolver to return the index.html for the language of the
    // Accept-Language header
    Locale locale = localeResolver.resolveLocale(request);
    return chain.resolveResource(request, locale.toLanguageTag() + "/index.html", locations);
  }

  @Override
  public String resolveUrlPath(
      @NonNull String resourcePath,
      @NonNull List<? extends Resource> locations,
      ResourceResolverChain chain) {
    return chain.resolveUrlPath(resourcePath, locations);
  }
}

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
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.springframework.web.util.UriUtils;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.repository.ConfigurationRepository;

/**
 * Resolver which returns index.html for requests to paths created by the Angular routing module. <br>
 * When the user refreshes the page such routes are requested from the server.
 */
@Component
public class FrontControllerResolver implements ResourceResolver, InitializingBean {

  private final ConfigurationRepository configurationRepository;
  private final ApplicationRepository applicationRepository;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @Value("#{'${tailormap-api.supported-languages:en}'.split(',')}")
  private List<String> supportedLanguages;

  private AcceptHeaderLocaleResolver localeResolver;

  private boolean staticOnly;

  private Pattern localeBundlePrefixPattern = Pattern.compile("^[a-z]{2}/.*");

  public FrontControllerResolver(
      // Inject these repositories lazily because in the static-only profile these are not needed
      // but also not configured
      @Lazy ConfigurationRepository configurationRepository, @Lazy ApplicationRepository applicationRepository) {
    this.configurationRepository = configurationRepository;
    this.applicationRepository = applicationRepository;
  }

  @Override
  public void afterPropertiesSet() {
    this.staticOnly = activeProfile.contains("static-only");

    this.localeResolver = new AcceptHeaderLocaleResolver();
    localeResolver.setSupportedLocales(
        supportedLanguages.stream().map(Locale::new).toList());
    localeResolver.setDefaultLocale(localeResolver.getSupportedLocales().get(0));
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
    String localePrefix = StringUtils.left(requestPath, 2);
    if ((localeBundlePrefixPattern.matcher(requestPath).matches() && supportedLanguages.contains(localePrefix))
        // When the request is just "GET /nl/" or "GET /nl" the requestPath is "nl" without a
        // trailing slash
        || supportedLanguages.contains(requestPath)) {
      return chain.resolveResource(request, localePrefix + "/index.html", locations);
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
        if (appLanguage != null) {
          resource = chain.resolveResource(request, appLanguage + "/index.html", locations);
          if (resource != null) {
            return resource;
          }
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
      @NonNull String resourcePath, @NonNull List<? extends Resource> locations, ResourceResolverChain chain) {
    return chain.resolveUrlPath(resourcePath, locations);
  }
}

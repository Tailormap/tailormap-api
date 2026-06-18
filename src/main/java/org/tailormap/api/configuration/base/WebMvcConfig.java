/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration.base;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.CachingResourceResolver;
import org.springframework.web.servlet.resource.EncodedResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.tailormap.api.configuration.CaseInsensitiveEnumConverter;
import org.tailormap.api.controller.LayerExtractController;
import org.tailormap.api.persistence.json.GeoServiceProtocol;
import org.tailormap.api.scheduling.TaskType;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
  private final FrontControllerResolver frontControllerResolver;
  private final IndexHtmlTransformer indexHtmlTransformer;
  private final IconResolver iconResolver;

  @Value("#{'${spring.web.resources.static-locations:file:/home/cnb/static/}'.split(',')}")
  private String[] staticResourceLocations;

  @Value("#{'${tailormap-api.web.icons:favicon.ico,favicon.svg,apple-touch-icon.png}'.split(',')}")
  private String[] iconFilenames;

  public WebMvcConfig(
      FrontControllerResolver frontControllerResolver,
      IndexHtmlTransformer indexHtmlTransformer,
      IconResolver iconResolver) {
    this.frontControllerResolver = frontControllerResolver;
    this.indexHtmlTransformer = indexHtmlTransformer;
    this.iconResolver = iconResolver;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    ResourceResolver encodedResourceResolver = new EncodedResourceResolver();
    registry.addResourceHandler("/version.json")
        .addResourceLocations(staticResourceLocations)
        .setCacheControl(CacheControl.noStore());

    CachingResourceResolver iconCachingResolver =
        new CachingResourceResolver(new ConcurrentMapCache("theme-favicon-cache"));
    String[] iconPatterns = Arrays.stream(iconFilenames)
        .map(String::trim)
        .filter(filename -> !filename.isEmpty())
        // Support both multiple localized bundles and a single locale bundle (/favicon.ico and /*/favicon.ico
        // matching /nl/favicon.ico)
        .flatMap(filename -> Stream.of("/" + filename, "/*/" + filename))
        .toArray(String[]::new);
    registry.addResourceHandler(iconPatterns)
        .addResourceLocations(staticResourceLocations)
        .setCachePeriod(5 * 60)
        .resourceChain(false)
        .addResolver(iconCachingResolver)
        .addResolver(iconResolver.setCachingResolver(iconCachingResolver));
    registry
        // Add cache headers for frontend bundle resources with hash in filename and fonts/images
        .addResourceHandler(
            "/*.js",
            "/*/*.js",
            "/*.css",
            "/*/*.css",
            "/media/**",
            "/*/media/**",
            "/icons/**",
            "/*/icons/**")
        .addResourceLocations(staticResourceLocations)
        .setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).mustRevalidate())
        .resourceChain(true)
        .addResolver(encodedResourceResolver);
    registry.addResourceHandler("/**")
        .addResourceLocations(staticResourceLocations)
        // no-cache means the browser must revalidate index.html with a conditional HTTP request
        // using If-Modified-Since. This is needed to always have the latest frontend loaded in the
        // browser after deployment of a new release.
        .setCacheControl(CacheControl.noCache())
        // Don't cache resources which can vary per user because of the Accept-Language header
        .resourceChain(false)
        .addResolver(frontControllerResolver)
        .addResolver(encodedResourceResolver)
        .addTransformer(indexHtmlTransformer);
  }

  @Override
  public void addFormatters(@NonNull FormatterRegistry registry) {
    registry.addConverter(
        String.class, GeoServiceProtocol.class, new CaseInsensitiveEnumConverter<>(GeoServiceProtocol.class));

    registry.addConverter(String.class, TaskType.class, new CaseInsensitiveEnumConverter<>(TaskType.class));
    registry.addConverter(
        String.class,
        LayerExtractController.ExtractOutputFormat.class,
        new CaseInsensitiveEnumConverter<>(LayerExtractController.ExtractOutputFormat.class));
  }
}

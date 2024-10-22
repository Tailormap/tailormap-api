/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration.base;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.EncodedResourceResolver;
import org.tailormap.api.configuration.CaseInsensitiveEnumConverter;
import org.tailormap.api.persistence.json.GeoServiceProtocol;
import org.tailormap.api.scheduling.TaskType;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
  private final IndexHtmlTransformer indexHtmlTransformer;

  @Value("${spring.web.resources.static-locations:file:/home/spring/static/}")
  private String resourceLocations;

  public WebMvcConfig(IndexHtmlTransformer indexHtmlTransformer) {
    this.indexHtmlTransformer = indexHtmlTransformer;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/*/index.html")
        .addResourceLocations(resourceLocations.split(",", -1)[0])
        // no-cache means the browser must revalidate index.html with a conditional HTTP request
        // using If-Modified-Since. This is needed to always have the latest frontend loaded in the
        // browser after deployment of a new release.
        .setCacheControl(CacheControl.noCache())
        .resourceChain(true)
        .addTransformer(indexHtmlTransformer);
    registry
        .addResourceHandler("/version.json")
        .addResourceLocations(resourceLocations.split(",", -1)[0])
        .setCacheControl(CacheControl.noStore());
    registry
        .addResourceHandler("/**")
        .addResourceLocations(resourceLocations.split(",", -1)[0])
        .resourceChain(true)
        .addResolver(new EncodedResourceResolver());
  }

  @Override
  public void addFormatters(@NonNull FormatterRegistry registry) {
    registry.addConverter(
        String.class,
        GeoServiceProtocol.class,
        new CaseInsensitiveEnumConverter<>(GeoServiceProtocol.class));

    registry.addConverter(
        String.class, TaskType.class, new CaseInsensitiveEnumConverter<>(TaskType.class));
  }
}

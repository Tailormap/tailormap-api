/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
  @Value("${spring.web.resources.static-locations:file:/home/spring/static/}")
  private String resourceLocations;

  private final IndexHtmlTransformer indexHtmlTransformer;

  public WebMvcConfig(IndexHtmlTransformer indexHtmlTransformer) {
    this.indexHtmlTransformer = indexHtmlTransformer;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/*/index.html")
        .addResourceLocations(resourceLocations.split(",")[0])
        // no-cache means the browser must revalidate index.html with a conditional HTTP request
        // using If-Modified-Since. This is needed to always have the latest frontend loaded in the
        // browser after deployment of a new release.
        .setCacheControl(CacheControl.noCache())
        .resourceChain(true)
        .addTransformer(indexHtmlTransformer);
  }
}

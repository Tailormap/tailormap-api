/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
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
        .resourceChain(true)
        .addTransformer(indexHtmlTransformer);
  }
}

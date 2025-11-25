/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import ch.rasc.sse.eventbus.config.EnableSseEventBus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;

import java.util.Locale;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "tailormap-api")
@EnableSseEventBus
@EnableScheduling
public class TailormapConfig {
  private int timeout;

  @Value("${tailormap-api.default-language:en}")
  private String defaultLanguage;

  public int getTimeout() {
    return timeout;
  }

  public TailormapConfig setTimeout(int timeout) {
    this.timeout = timeout;
    return this;
  }

  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(Locale.of(defaultLanguage));
    return resolver;
  }

    @Bean
    JsonMapperBuilderCustomizer jacksonCustomizer() {
        return builder ->
                builder.enable(SerializationFeature.INDENT_OUTPUT)
                        .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
                        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                ;
    }
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import ch.rasc.sse.eventbus.config.EnableSseEventBus;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "tailormap-api")
@EnableSseEventBus
@EnableScheduling
public class TailormapConfig {
  private int timeout;

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
    resolver.setDefaultLocale(Locale.ENGLISH);
    return resolver;
  }
}

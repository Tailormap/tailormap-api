/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import ch.rasc.sse.eventbus.DataObjectConverter;
import ch.rasc.sse.eventbus.DefaultDataObjectConverter;
import ch.rasc.sse.eventbus.DefaultSubscriptionRegistry;
import ch.rasc.sse.eventbus.DistributedEventBus;
import ch.rasc.sse.eventbus.JacksonDataObjectConverter;
import ch.rasc.sse.eventbus.ReplayStore;
import ch.rasc.sse.eventbus.SseEventBus;
import ch.rasc.sse.eventbus.SubscriptionRegistry;
import ch.rasc.sse.eventbus.config.EnableSseEventBus;
import ch.rasc.sse.eventbus.config.SseEventBusConfigurer;
import ch.rasc.sse.eventbus.observation.SseEventBusObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.ObjectMapper;

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

  /**
   * Define a new viewer SseEventBus bean for viewer-specific SSE traffic.
   *
   * @return the viewerSseEventBus instance
   */
  @Bean("viewerSseEventBus")
  public SseEventBus viewerSseEventBus(
      @Autowired(required = false) @Nullable SseEventBusConfigurer configurer,
      @Autowired(required = false) @Nullable ObjectMapper objectMapper,
      @Autowired(required = false) @Nullable List<DataObjectConverter> dataObjectConverters,
      @Autowired(required = false) @Nullable SubscriptionRegistry subscriptionRegistry,
      @Autowired(required = false) @Nullable ReplayStore replayStore,
      @Autowired(required = false) @Nullable ObservationRegistry observationRegistry,
      @Autowired(required = false) @Nullable SseEventBusObservationConvention observationConvention,
      @Autowired(required = false) @Nullable DistributedEventBus distributedEventBus) {

    // Apply same defaults as DefaultSseEventBusConfiguration
    SseEventBusConfigurer config = configurer != null
        ? configurer
        : new SseEventBusConfigurer() {
          /* defaults */
        };

    SubscriptionRegistry registry =
        subscriptionRegistry != null ? subscriptionRegistry : new DefaultSubscriptionRegistry();

    ReplayStore store = replayStore != null ? replayStore : config.replayStore();

    List<DataObjectConverter> converters =
        dataObjectConverters != null ? new ArrayList<>(dataObjectConverters) : new ArrayList<>();
    if (converters.isEmpty()) {
      if (objectMapper != null) {
        converters.add(new JacksonDataObjectConverter(objectMapper));
      } else {
        converters.add(new DefaultDataObjectConverter());
      }
    }

    return new SseEventBus(
        config, registry, converters, store, observationRegistry, observationConvention, distributedEventBus);
  }
}

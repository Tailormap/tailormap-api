/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence.helper;

import static ch.rasc.sse.eventbus.SseEvent.DEFAULT_EVENT;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.tailormap.api.admin.model.CapabilitiesLoadingEvent;
import org.tailormap.api.admin.model.ServerSentEvent;
import org.tailormap.api.geotools.featuresources.JDBCFeatureSourceHelper;
import org.tailormap.api.geotools.featuresources.WFSFeatureSourceHelper;
import org.tailormap.api.persistence.TMFeatureSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TMFeatureSourceHelper implements CapabilitiesLoadingProgressReporting {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Value("${tailormap-api.timeout}")
  private int timeout;

  private final SseEventBus eventBus;
  private final JsonMapper mapper;

  public TMFeatureSourceHelper(SseEventBus eventBus, JsonMapper mapper) {
    this.eventBus = eventBus;
    this.mapper = mapper;
  }

  public TMFeatureSource loadCapabilities(TMFeatureSource featureSource) throws IOException {
    final Instant startedAt = Instant.now();
    final OffsetDateTime startedAtOffset =
        startedAt.atOffset(ZoneId.systemDefault().getRules().getOffset(startedAt));
    CapabilitiesLoadingEvent event = new CapabilitiesLoadingEvent()
        .startedAt(startedAtOffset)
        .title(
            StringUtils.isNotBlank(featureSource.getTitle())
                ? featureSource.getTitle()
                : TMFeatureSource.Protocol.JDBC.equals(featureSource.getProtocol())
                    ? String.valueOf(featureSource
                        .getJdbcConnection()
                        .getDatabase())
                    : featureSource.getUrl())
        .id(featureSource.getId() != null ? featureSource.getId().toString() : null);

    switch (featureSource.getProtocol()) {
      case WFS -> new WFSFeatureSourceHelper().loadCapabilities(featureSource, timeout, this, event);
      case JDBC -> new JDBCFeatureSourceHelper().loadCapabilities(featureSource, timeout, this, event);
      default -> throw new UnsupportedOperationException("Unsupported protocol: " + featureSource.getProtocol());
    }

    reportCapabilitiesLoadingProgress(event.message("Finished loading feature source capabilities in "
            + (Instant.now().toEpochMilli() - startedAt.toEpochMilli()) + " ms")
        .progress(event.getTotal()));
    return featureSource;
  }

  public TMFeatureSource createFeatureSource(TMFeatureSource featureSource)
      throws IOException, IllegalArgumentException {
    switch (featureSource.getProtocol()) {
      case WFS -> {
        if (StringUtils.isBlank(featureSource.getUrl())) {
          throw new IllegalArgumentException("WFS feature source requires a URL");
        }
        URI uri;
        try {
          uri = new URI(featureSource.getUrl());
        } catch (URISyntaxException | NullPointerException e) {
          throw new IllegalArgumentException("Invalid URI");
        }
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
          throw new IllegalArgumentException("Invalid URI scheme");
        }
      }
      case JDBC -> {
        if (featureSource.getJdbcConnection() == null) {
          throw new IllegalArgumentException("JDBC connection properties are required");
        }
        if (featureSource.getAuthentication() == null) {
          throw new IllegalArgumentException("Database username and password are required");
        }
      }
    }

    return loadCapabilities(featureSource);
  }

  @Override
  public void reportCapabilitiesLoadingProgress(CapabilitiesLoadingEvent event) {
    ServerSentEvent serverSentEvent = new ServerSentEvent()
        .eventType(ServerSentEvent.EventTypeEnum.CAPABILITIES_LOADING)
        .details(event);
    try {
      if (this.eventBus.countSubscribers(DEFAULT_EVENT) == 0) {
        return;
      }
      this.eventBus.handleEvent(SseEvent.of(DEFAULT_EVENT, mapper.writeValueAsString(serverSentEvent)));
    } catch (JacksonException e) {
      logger.error("Error publishing feature source capabilities loading progress event", e);
    }
  }
}

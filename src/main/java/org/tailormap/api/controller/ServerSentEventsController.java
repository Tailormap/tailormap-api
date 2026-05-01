/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static ch.rasc.sse.eventbus.SseEvent.DEFAULT_EVENT;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.tailormap.api.viewer.model.ServerSentEventResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class ServerSentEventsController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final SseEventBus eventBus;

  private final JsonMapper jsonMapper;

  public ServerSentEventsController(SseEventBus eventBus, JsonMapper jsonMapper) {
    this.eventBus = eventBus;
    // force unindented/single line output for SSE messages, because we may have set
    // spring.jackson.serialization.indent_output=true for debugging/development/test
    if (jsonMapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
      this.jsonMapper = jsonMapper
          .rebuild()
          .configure(SerializationFeature.INDENT_OUTPUT, false)
          .build();
    } else {
      this.jsonMapper = jsonMapper;
    }
  }

  @GetMapping(path = "${tailormap-api.base-path}/events/{clientId}")
  public SseEmitter sse(@PathVariable String clientId) {
    // tests input against the set allowed by Nano ID
    if (!clientId.matches("[A-Za-z0-9_-]+")) {
      logger.warn("Invalid clientId for SSE connection: {}", clientId);
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid clientId");
    }
    logger.debug("Adding new SSE client with id: {}", clientId);
    return this.eventBus.createSseEmitter(clientId, 3600_000L, DEFAULT_EVENT);
  }

  @Scheduled(fixedRate = 60_000)
  public void keepAlive() throws JacksonException {
    this.eventBus.handleEvent(SseEvent.ofData(jsonMapper.writeValueAsString(
        new ServerSentEventResponse().eventType(ServerSentEventResponse.EventTypeEnum.KEEP_ALIVE))));
  }
}

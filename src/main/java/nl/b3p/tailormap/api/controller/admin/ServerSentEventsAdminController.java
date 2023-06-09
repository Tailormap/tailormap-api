/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller.admin;

import static ch.rasc.sse.eventbus.SseEvent.DEFAULT_EVENT;
import static nl.b3p.tailormap.api.admin.model.ServerSentEvent.EventTypeEnum.KEEP_ALIVE;

import ch.rasc.sse.eventbus.SseEvent;
import ch.rasc.sse.eventbus.SseEventBus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.invoke.MethodHandles;
import nl.b3p.tailormap.api.admin.model.ServerSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ServerSentEventsAdminController {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final SseEventBus eventBus;

  private final ObjectMapper objectMapper;

  public ServerSentEventsAdminController(SseEventBus eventBus, ObjectMapper objectMapper) {
    this.eventBus = eventBus;
    this.objectMapper = objectMapper;
  }

  /**
   * Endpoint for the single-page admin frontend to receive updates to entities from other windows.
   * The frontend does not load all data on each navigation as a traditional frontend and loads and
   * stores entities in memory. This requires updating stale entities when these get updated in
   * other windows (from other sessions or the same session in a different browser tab), otherwise
   * data might get lost or overwritten.
   *
   * <p>This endpoint uses <a
   * href="https://html.spec.whatwg.org/multipage/server-sent-events.html">Server-sent events</a>,
   * which technically is a long-poll HTTP request which allows unidirectional server-initiated
   * communication. Not bidirectional like websockets, but simpler because of the long-poll HTTP
   * request. The webserver should ideally use HTTP/2 or higher because HTTP/1.1 limits the amount
   * of simultaneous connections to a server per browser to 6.
   *
   * @return the server-sent events emitter
   */
  @GetMapping(path = "${tailormap-api.admin.base-path}/events/{clientId}")
  public SseEmitter entityEvents(
      /*@RequestParam(required = false) String[] events,*/ @PathVariable("clientId")
          String clientId) {
    logger.debug("New SSE client: {}, all clients: {}", clientId, this.eventBus.getAllClientIds());
    return this.eventBus.createSseEmitter(clientId, DEFAULT_EVENT);
  }

  @Scheduled(fixedRate = 60_000)
  public void keepAlive() throws JsonProcessingException {
    this.eventBus.handleEvent(
        SseEvent.ofData(
            objectMapper.writeValueAsString(new ServerSentEvent().eventType(KEEP_ALIVE))));
  }
}

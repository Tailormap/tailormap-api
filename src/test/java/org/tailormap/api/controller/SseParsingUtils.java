/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.tailormap.api.viewer.model.ServerSentEventResponse.EventTypeEnum;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

abstract class SseParsingUtils {

  /**
   * Parse the {@code data:} lines from an SSE stream as JSON events. Each event looks something like:
   * {@code {"details":{"message":"Extract task
   * completed","progress":100,"downloadId":"begroeidterreindeel15061479295163305053.csv"},"eventType":"extract-completed","id":"019d6838-7f48-7053-9256-dd4b57c14264"}
   * }
   */
  private static Stream<JsonNode> parseEvents(String sseMessages) {
    return Arrays.stream(sseMessages.split("\\R"))
        .map(String::trim)
        .filter(line -> line.startsWith("data:"))
        .map(line -> JsonMapper.shared().readTree(line.substring("data:".length())));
  }

  private static Stream<JsonNode> parseEvents(String sseMessages, EventTypeEnum eventType) {
    return parseEvents(sseMessages)
        .filter(event ->
            eventType.getValue().equals(event.path("eventType").asString()));
  }

  /** Get the last extract completed event from the SSE stream as JSON. */
  String getLastCompletedEventJson(String sseMessages) {
    List<JsonNode> completedEvents =
        parseEvents(sseMessages, EventTypeEnum.EXTRACT_COMPLETED).toList();
    // Select last completed event, throws NoSuchElementException if there is none
    return completedEvents.getLast().toString();
  }

  String getDownloadId(String eventJson) throws JacksonException {
    return JsonMapper.shared()
        .readTree(eventJson)
        .path("details")
        .path("downloadId")
        .asString();
  }

  int count_completed_messages(String stream) {
    return (int) parseEvents(stream, EventTypeEnum.EXTRACT_COMPLETED).count();
  }

  int count_all_keep_alive_messages(String stream) {
    return (int) parseEvents(stream, EventTypeEnum.KEEP_ALIVE).count();
  }

  /** Count the keep-alive messages of the viewer event bus, which have empty details and an id. */
  int count_viewer_keep_alive_messages(String stream) {
    return (int) parseEvents(stream, EventTypeEnum.KEEP_ALIVE)
        .filter(event -> event.path("details").isObject()
            && event.path("details").isEmpty()
            && event.has("id"))
        .count();
  }

  /** Count the keep-alive messages of the admin event bus, which have null details. */
  int count_admin_keep_alive_messages(String stream) {
    return (int) parseEvents(stream, EventTypeEnum.KEEP_ALIVE)
        .filter(event -> event.has("details") && event.get("details").isNull())
        .count();
  }
}

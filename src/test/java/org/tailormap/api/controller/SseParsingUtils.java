/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import org.tailormap.api.viewer.model.ServerSentEventResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

abstract class SseParsingUtils {

  /**
   * Parse the last non-empty line from the SSE stream that looks something like:
   * {@code data:{"details":{"message":"Extract task
   * completed","progress":100,"downloadId":"begroeidterreindeel15061479295163305053.csv"},"eventType":"extract-completed","id":"019d6838-7f48-7053-9256-dd4b57c14264"}
   * } as JSON and extract the file from the details.
   */
  String getLastCompletedEventJson(String sseMessages) {
    return java.util.Arrays.stream(sseMessages.split("\\R"))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .filter(line -> line.startsWith("data:"))
        .filter(line -> line.contains("\"eventType\":\"extract-completed\""))
        .reduce((first, second) -> second)
        .orElseThrow()
        .substring("data:".length());
  }

  String getDownloadId(String eventJson) throws JacksonException {
    return new ObjectMapper()
        .readTree(eventJson)
        .path("details")
        .path("downloadId")
        .asString();
  }

  int count_completed_messages(String s) {
    int count = 0;
    int index = 0;
    final String marker = "\"eventType\":\"" + ServerSentEventResponse.EventTypeEnum.EXTRACT_COMPLETED + "\"";
    while ((index = s.indexOf(marker, index)) != -1) {
      count++;
      index += marker.length();
    }
    return count;
  }
}

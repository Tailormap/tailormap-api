/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.viewer.model.ServerSentEventResponse;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class ServerSentEventsControllerIntegrationTest {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  // Unique id avoids interference with parallel/other tests.
  private final String sseClientId = "keepalive-test-" + System.nanoTime();

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  private MvcResult sseResult;

  @BeforeEach
  void start_sse_stream() throws Exception {
    final String sseUrl = apiBasePath + "/events/" + sseClientId;
    sseResult = mockMvc.perform(get(sseUrl)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .with(setServletPath(sseUrl))
            .acceptCharset(StandardCharsets.UTF_8))
        .andExpect(request().asyncStarted())
        .andReturn();
  }

  /** Check that at least 2 keep-alive messages arrive in 130 seconds. */
  @Test
  void should_send_keep_alive_messages_for_two_minutes() {
    // Keep this test running for at least 2 minutes, then assert at least 2 keep-alives arrived.
    Awaitility.await("waiting for keep-alive messages")
        .pollDelay(45, SECONDS)
        .pollInterval(15, SECONDS)
        .atLeast(2, MINUTES)
        .atMost(130, SECONDS)
        .logging(logPrinter -> logger.debug("Checking for keep-alive messages in SSE stream... {}", logPrinter))
        .untilAsserted(() -> {
          final String stream = sseResult.getResponse().getContentAsString();
          assertThat(count_keep_alive_messages(stream), greaterThanOrEqualTo(2));
        });
  }

  private int count_keep_alive_messages(String stream) {
    int count = 0;
    int index = 0;
    final String marker = "\"eventType\":\"" + ServerSentEventResponse.EventTypeEnum.KEEP_ALIVE + "\"";
    while ((index = stream.indexOf(marker, index)) != -1) {
      count++;
      index += marker.length();
    }
    return count;
  }
}

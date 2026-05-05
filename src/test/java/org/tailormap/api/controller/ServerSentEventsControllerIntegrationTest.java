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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class ServerSentEventsControllerIntegrationTest extends SseParsingUtils {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  // Unique id avoids interference with parallel/other tests.
  private final String sseClientId = "keepalive-test-" + System.nanoTime();

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Autowired
  private WebApplicationContext context;

  private MvcResult sseResult;

  @BeforeEach
  void start_sse_stream() throws Exception {
    final String sseUrl = apiBasePath + "/events/" + sseClientId;
    sseResult = mockMvc.perform(get(sseUrl)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .with(setServletPath(sseUrl))
            .acceptCharset(StandardCharsets.UTF_8))
        .andExpect(status().isOk())
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
        .atLeast(1, MINUTES)
        .atMost(130, SECONDS)
        .logging(logPrinter -> logger.debug("Checking for keep-alive messages in SSE stream... {}", logPrinter))
        .untilAsserted(() -> {
          final String stream = sseResult.getResponse().getContentAsString();
          assertThat(count_all_keep_alive_messages(stream), greaterThanOrEqualTo(2));
        });
  }

  /** Check that at least 2 keep-alive messages arrive in 130 seconds. */
  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void admin_and_viewer_should_use_separate_sse_streams() throws Exception {
    // start admin sse stream
    MockMvc adminMockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    final String adminSseUrl = adminBasePath + "/events/" + sseClientId;
    MvcResult adminSseResult = adminMockMvc
        .perform(get(adminSseUrl)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .with(setServletPath(adminSseUrl))
            .acceptCharset(StandardCharsets.UTF_8))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted())
        .andReturn();

    Awaitility.await("Waiting at least 2 minutes for any keep-alive messages")
        .pollDelay(45, SECONDS)
        .pollInterval(15, SECONDS)
        .atLeast(1, MINUTES)
        .atMost(130, SECONDS)
        .logging(
            logPrinter -> logger.debug("Checking for keep-alive messages in SSE streams... {}", logPrinter))
        .untilAsserted(() -> {
          // check admin stream
          final String adminStream = adminSseResult.getResponse().getContentAsString();
          logger.debug("admin stream: {}", adminStream);
          assertThat(
              "There should be at least 2 keep-alive messages for the admin",
              count_all_keep_alive_messages(adminStream),
              greaterThanOrEqualTo(2));
          assertEquals(
              0,
              count_viewer_keep_alive_messages(adminStream),
              "There should be no keep-alive messages for the viewer in the admin");
          assertEquals(
              count_all_keep_alive_messages(adminStream),
              count_admin_keep_alive_messages(adminStream),
              "We should only get admin keep-alive messages in the admin SSE stream");

          // and viewer stream
          final String stream = sseResult.getResponse().getContentAsString();
          logger.debug("viewer stream: {}", stream);
          assertThat(
              "There should be at least 2 keep-alive messages for the viewer",
              count_all_keep_alive_messages(stream),
              greaterThanOrEqualTo(2));
          assertEquals(
              count_all_keep_alive_messages(stream),
              count_viewer_keep_alive_messages(stream),
              "Admin keep-alive messages should not be sent to viewer SSE stream");
          assertEquals(
              0,
              count_admin_keep_alive_messages(stream),
              "There should be no keep-alive messages for the admin in the viewer SSE stream");
        });
  }
}

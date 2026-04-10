/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class ServerSentEventsControllerInvalidInputIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void invalid_client_id_should_return_bad_request() throws Exception {
    final String invalidClientId = "invalid-te$t-" + System.nanoTime();
    final String sseUrl = apiBasePath + "/events/" + invalidClientId;

    mockMvc.perform(get(sseUrl).accept(MediaType.TEXT_EVENT_STREAM).with(setServletPath(sseUrl)))
        .andExpect(status().isBadRequest());
  }
}

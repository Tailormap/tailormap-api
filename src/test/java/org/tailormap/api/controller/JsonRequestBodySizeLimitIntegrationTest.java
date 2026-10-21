/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.persistence.Group.ADMIN;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;

/** Test for the {@code spring.jackson.factory.constraints.read.max-document-length} limit on JSON request bodies. */
@PostgresIntegrationTest
@AutoConfigureMockMvc
class JsonRequestBodySizeLimitIntegrationTest {
  private static final int MAX_DOCUMENT_LENGTH = 15 * 1024 * 1024;

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void drawing_exceeding_max_document_length_is_rejected() throws Exception {
    // the description is well below the max string length, but the document exceeds the max document length
    final String drawing = """
{"name":"Too large drawing","description":"%s","domainData":{},"featureCollection":null}
""".formatted("x".repeat(MAX_DOCUMENT_LENGTH));

    final String url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(drawing)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }
}

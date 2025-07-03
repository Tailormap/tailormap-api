/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@PostgresIntegrationTest
class FindUploadsByHashControllerTest {
  @Autowired
  private WebApplicationContext context;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void testUploadMatches() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs
    String body = """
[
"30a69db6165c325c174187e3182f83a2",
"334c4a4c42fdb79d7ebc3e73b517e6f8"
]
""";

    String expected =
        """
[
{
"id": "1c24fcfa-2c68-476b-b49b-baf172726e5d",
"hash": "30a69db6165c325c174187e3182f83a2"
}
]""";

    mockMvc.perform(post(adminBasePath + "/uploads/find-by-hash/drawing-style-image")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().is2xxSuccessful())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(expected));
  }
}

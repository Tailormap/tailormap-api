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
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.repository.UploadRepository;

@PostgresIntegrationTest
class FindUploadsByHashControllerIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private UploadRepository uploadRepository;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void testUploadMatches() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs
    String body =
        """
[
"108d266b132661285a7533339958a77dfd06821a",
"71f8e7976e4cbc4561c9d62fb283e7f788202acb"
]
""";

    Upload drinkwater = uploadRepository.findByFilename("drinkwater.svg").stream()
        .findAny()
        .orElseThrow(() -> new IllegalStateException(
            "Expected upload with filename 'drinkwater.svg' not found in the database"));

    String expected = String.format(
        """
[
{
"id": "%s",
"hash": "108d266b132661285a7533339958a77dfd06821a"
}
]""",
        drinkwater.getId().toString());

    mockMvc.perform(post(adminBasePath + "/uploads/find-by-hash/drawing-style-image")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().is2xxSuccessful())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(expected));
  }
}

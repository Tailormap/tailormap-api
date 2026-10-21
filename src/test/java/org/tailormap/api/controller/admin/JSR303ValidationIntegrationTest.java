/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestJson.json;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
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
@Execution(ExecutionMode.CONCURRENT)
class JSR303ValidationIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void validate_url_required() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    // A mutable map, because Map.of() does not allow null values
    final Map<String, Object> geoService = new HashMap<>(Map.of("protocol", "wms", "title", "test"));
    geoService.put("url", null);
    final String geoServicePOSTBody = json(geoService);

    mockMvc.perform(post(adminBasePath + "/geo-services")
            .contentType(MediaType.APPLICATION_JSON)
            .content(geoServicePOSTBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].entity").value("GeoService"))
        .andExpect(jsonPath("$.errors[0].property").value("url"))
        .andExpect(jsonPath("$.errors[0].invalidValue").isEmpty())
        .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
  }
}

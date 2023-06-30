/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostgresIntegrationTest
public class JSR303ValidationIntegrationTest {
  @Autowired private WebApplicationContext context;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void testUrlRequired() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    String geoServicePOSTBody =
        new ObjectMapper()
            .createObjectNode()
            .put("protocol", "wms")
            .put("title", "test")
            .put("refreshCapabilities", true)
            .put("url", (String) null)
            .toPrettyString();

    mockMvc
        .perform(
            post(adminBasePath + "/geo-services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(geoServicePOSTBody))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"errors\":[{\"entity\":\"GeoService\",\"property\":\"url\",\"invalidValue\":null,\"message\":\"must not be null\"}]}"));
  }
}

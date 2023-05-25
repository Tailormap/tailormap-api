package nl.b3p.tailormap.api.controller.admin;

import static nl.b3p.tailormap.api.StaticTestData.getResourceString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
@PostgresIntegrationTest
class GeoServiceAdminControllerIntegrationTest {
  @Autowired private WebApplicationContext context;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Value("classpath:/wms/test_capabilities.xml")
  private Resource wmsTestCapabilities;

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void refreshCapabilities() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    String body = getResourceString(wmsTestCapabilities);
    final Headers contentType =
        new Headers(new String[] {"Content-Type", "application/vnd.ogc.wms_xml"});

    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse().setHeaders(contentType).setBody(body));
      server.start();

      String url = server.url("/test-wms").toString();

      String geoServicePOSTBody =
          new ObjectMapper()
              .createObjectNode()
              .put("protocol", "wms")
              .put("url", url)
              .toPrettyString();

      mockMvc
          .perform(
              post(adminBasePath + "/geo-services")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(geoServicePOSTBody))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.layers").isArray())
          .andExpect(jsonPath("$.layers.length()").value(2))
          .andExpect(jsonPath("$.layers[0].title").value("Test Layer 1"))
          .andExpect(jsonPath("$.layers[1].name").value("Layer2"));

      RecordedRequest request = server.takeRequest();
      assertEquals("GetCapabilities", request.getRequestUrl().queryParameter("REQUEST"));
      server.shutdown();
    }
  }
}

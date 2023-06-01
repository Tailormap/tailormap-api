package nl.b3p.tailormap.api.controller.admin;

import static nl.b3p.tailormap.api.StaticTestData.getResourceString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
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
import org.springframework.test.web.servlet.MvcResult;
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

  @Value("classpath:/wms/test_capabilities_updated.xml")
  private Resource wmsTestCapabilitiesUpdated;

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

      MvcResult result =
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
              .andExpect(jsonPath("$.layers[1].name").value("Layer2"))
              .andReturn();
      String serviceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
      String selfLink =
          JsonPath.read(result.getResponse().getContentAsString(), "$._links.self.href");

      assertEquals(
          "GetCapabilities", server.takeRequest().getRequestUrl().queryParameter("REQUEST"));

      // This capabilities document has an extra layer
      body = getResourceString(wmsTestCapabilitiesUpdated);
      server.enqueue(new MockResponse().setHeaders(contentType).setBody(body));

      mockMvc
          .perform(
              post(
                  adminBasePath
                      + String.format("/geo-services/%s/refresh-capabilities", serviceId)))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", equalTo(selfLink)));

      assertEquals(
          "GetCapabilities", server.takeRequest().getRequestUrl().queryParameter("REQUEST"));

      mockMvc
          .perform(get(selfLink).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.layers").isArray())
          .andExpect(jsonPath("$.layers.length()").value(3))
          .andExpect(jsonPath("$.layers[0].title").value("Test Layer 1"))
          .andExpect(jsonPath("$.layers[0].children[0]").value(1))
          .andExpect(jsonPath("$.layers[0].children[1]").value(2))
          .andExpect(jsonPath("$.layers[1].name").value("Layer2"))
          .andExpect(jsonPath("$.layers[2].name").value("Layer3"));

      server.shutdown();
    }
  }
}

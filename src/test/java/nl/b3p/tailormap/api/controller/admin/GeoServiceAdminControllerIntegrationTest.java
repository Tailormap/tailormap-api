package nl.b3p.tailormap.api.controller.admin;

import static nl.b3p.tailormap.api.StaticTestData.getResourceString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.util.Objects;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import okhttp3.Headers;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
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

  @Value("classpath:/wms/service_exception_1_0_0.xml")
  private Resource wmsServiceException1_0_0;

  @Value("classpath:/wms/service_exception_1_3_0.xml")
  private Resource wmsServiceException1_3_0;

  private static ObjectNode getGeoServicePOSTBody(String url) {
    return new ObjectMapper()
        .createObjectNode()
        .put("protocol", "wms")
        .put("title", "test")
        .put("refreshCapabilities", true)
        .put("url", url);
  }

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
      server.enqueue(new MockResponse.Builder().headers(contentType).body(body).build());
      server.start();

      String url = server.url("/test-wms").toString();
      String geoServicePOSTBody = getGeoServicePOSTBody(url).toPrettyString();

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
              .andExpect(jsonPath("$.layers[0].crs.length()").value(4))
              .andExpect(
                  jsonPath(
                      "$.layers[0].crs",
                      Matchers.containsInAnyOrder(
                          "EPSG:900913", "EPSG:4326", "EPSG:3857", "EPSG:28992")))
              .andExpect(jsonPath("$.layers[1].name").value("Layer2"))
              // Child layer inherits all parent CRSes, should not duplicate those to save space
              .andExpect(jsonPath("$.layers[1].crs.length()").value(0))
              .andReturn();
      String serviceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
      String selfLink =
          JsonPath.read(result.getResponse().getContentAsString(), "$._links.self.href");

      assertEquals(
          "GetCapabilities",
          Objects.requireNonNull(server.takeRequest().getRequestUrl()).queryParameter("REQUEST"));

      // This capabilities document has an extra layer
      body = getResourceString(wmsTestCapabilitiesUpdated);
      server.enqueue(new MockResponse.Builder().headers(contentType).body(body).build());

      mockMvc
          .perform(
              post(
                  adminBasePath
                      + String.format("/geo-services/%s/refresh-capabilities", serviceId)))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", equalTo(selfLink)));

      assertEquals(
          "GetCapabilities",
          Objects.requireNonNull(server.takeRequest().getRequestUrl()).queryParameter("REQUEST"));

      mockMvc
          .perform(get(selfLink).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.layers").isArray())
          .andExpect(jsonPath("$.layers.length()").value(3))
          .andExpect(jsonPath("$.layers[0].title").value("Test Layer 1"))
          .andExpect(jsonPath("$.layers[0].crs.length()").value(2))
          .andExpect(
              jsonPath("$.layers[0].crs", Matchers.containsInAnyOrder("EPSG:28992", "EPSG:900913")))
          .andExpect(jsonPath("$.layers[0].children[0]").value(1))
          .andExpect(jsonPath("$.layers[0].children[1]").value(2))
          .andExpect(jsonPath("$.layers[1].name").value("Layer2"))
          .andExpect(jsonPath("$.layers[1].crs.length()").value(1))
          .andExpect(jsonPath("$.layers[1].crs[0]").value("EPSG:3857"))
          .andExpect(jsonPath("$.layers[2].name").value("Layer3"))
          .andExpect(jsonPath("$.layers[2].crs.length()").value(1))
          .andExpect(jsonPath("$.layers[2].crs[0]").value("EPSG:4326"));

      server.shutdown();
    }
  }

  /**
   * Tests whether {@link AdminRepositoryRestExceptionHandler} is applied and converting validation
   * exceptions to JSON.
   */
  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void refreshCapabilitiesSendsJsonValidationErrors() throws Exception {

    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    // Create a GeoService without loading capabilities, with a valid URL but which points to an
    // invalid host. In the normal admin a user can change the URL of a service and the frontend
    // won't send refreshCapabilities but will ask to explicitly refresh the capabilities after
    // saving.

    String geoServicePOSTBody =
        getGeoServicePOSTBody("http://offline.invalid/")
            .put("refreshCapabilities", false)
            .toPrettyString();

    MvcResult result =
        mockMvc
            .perform(
                post(adminBasePath + "/geo-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(geoServicePOSTBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.layers").isEmpty())
            .andReturn();
    String serviceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(
            post(adminBasePath + String.format("/geo-services/%s/refresh-capabilities", serviceId)))
        .andExpect(status().isBadRequest())
        .andExpect(
            content()
                .json(
                    "{\"errors\":[{\"entity\":\"GeoService\",\"property\":\"url\",\"invalidValue\":\"http://offline.invalid/\",\"message\":\"Unknown host: \\\"offline.invalid\\\"\"}]}"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void loadServiceWithServiceException() throws Exception {

    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse.Builder()
              .headers(new Headers(new String[] {"Content-Type", "text/xml"}))
              .body(getResourceString(wmsServiceException1_0_0))
              .build());

      server.start();

      String url = server.url("/test-wms").toString();
      String geoServicePOSTBody = getGeoServicePOSTBody(url).toPrettyString();

      mockMvc
          .perform(
              post(adminBasePath + "/geo-services")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(geoServicePOSTBody))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value(
                      "Error loading capabilities from URL \""
                          + url
                          + "\": Exception: Error loading WMS capabilities: code: InvalidParameterValue: locator: service: Example error message"));

      server.enqueue(
          new MockResponse.Builder()
              .headers(new Headers(new String[] {"Content-Type", "text/xml"}))
              .body(getResourceString(wmsServiceException1_3_0))
              .build());

      mockMvc
          .perform(
              post(adminBasePath + "/geo-services")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(geoServicePOSTBody))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value(
                      "Error loading capabilities from URL \""
                          + url
                          + "\": Exception: Error loading WMS capabilities: code: SomeCode: locator: somewhere: An example error text."));

      server.shutdown();
    }
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void loadServiceWithWrongCredentials() throws Exception {

    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(new MockResponse.Builder().code(HttpStatus.UNAUTHORIZED.value()).build());
      server.start();

      String url = server.url("/test-wms").toString();
      String geoServicePOSTBody = getGeoServicePOSTBody(url).toPrettyString();

      mockMvc
          .perform(
              post(adminBasePath + "/geo-services")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(geoServicePOSTBody))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors[0].message")
                  .value(
                      "Error loading capabilities from URL \""
                          + url
                          + "\": Exception: Error loading WMS, got 401 unauthorized response (credentials may be required or invalid)"));
      server.shutdown();
    }
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void checkCorsHeaderIsSaved() throws Exception {

    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    try (MockWebServer server = new MockWebServer()) {

      server.enqueue(
          new MockResponse.Builder()
              .headers(
                  new Headers(
                      new String[] {
                        "Content-Type",
                        "application/vnd.ogc.wms_xml",
                        "Access-Control-Allow-Origin",
                        "https://my-origin"
                      }))
              .body(getResourceString(wmsTestCapabilities))
              .build());
      server.start();

      String url = server.url("/test-wms").toString();
      String geoServicePOSTBody = getGeoServicePOSTBody(url).toPrettyString();

      mockMvc
          .perform(
              post(adminBasePath + "/geo-services")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(geoServicePOSTBody))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.serviceCapabilities.corsAllowOrigin").value("https://my-origin"));
      server.shutdown();
    }
  }
}

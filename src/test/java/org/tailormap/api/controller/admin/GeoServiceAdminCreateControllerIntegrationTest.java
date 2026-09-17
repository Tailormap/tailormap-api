/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import org.tailormap.api.persistence.Catalog;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.json.CatalogNode;
import org.tailormap.api.persistence.json.TailormapObjectRef;
import org.tailormap.api.repository.CatalogRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@AutoConfigureMockMvc
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
class GeoServiceAdminCreateControllerIntegrationTest {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private CatalogRepository catalogRepository;

  @Autowired
  private JsonMapper jsonMapper;

  private MockMvc mockMvc;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  private static ObjectNode getGeoServicePOSTBody(String url) {
    return new JsonMapper()
        .createObjectNode()
        .put("protocol", "wms")
        .put("title", "test")
        .put("catalogNodeId", "GeoServiceAdminController")
        .put("url", url);
  }

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void create_geo_service_with_invalid_url() throws Exception {
    String geoServicePOSTBody = getGeoServicePOSTBody("http://invalid-url").toPrettyString();
    mockMvc.perform(post(adminBasePath + "/geo-services/new")
            .contentType(MediaType.APPLICATION_JSON)
            .content(geoServicePOSTBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Unknown host: \"invalid-url\"")));
  }

  @Test
  void create_geo_service_with_invalid_uri() throws Exception {
    String geoServicePOSTBody = getGeoServicePOSTBody("ftp://invalid-url").toPrettyString();
    mockMvc.perform(post(adminBasePath + "/geo-services/new")
            .contentType(MediaType.APPLICATION_JSON)
            .content(geoServicePOSTBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("Invalid URI scheme")));
  }

  @Test
  void create_geo_service_with_invalid_protocol() throws Exception {
    String geoServicePOSTBody = jsonMapper
        .createObjectNode()
        .put("protocol", "invalid-protocol")
        .put("title", "invalid test")
        .put("catalogNodeId", "GeoServiceAdminController")
        .put("url", "http://example.com")
        .toPrettyString();

    mockMvc.perform(post(adminBasePath + "/geo-services/new")
            .contentType(MediaType.APPLICATION_JSON)
            .content(geoServicePOSTBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.message")
            .value(containsStringIgnoringCase("problem: Unexpected value 'invalid-protocol'")));
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void create_geo_service() throws Exception {
    String geoServicePOSTBody = jsonMapper
        .createObjectNode()
        .put("protocol", "wms")
        .put("title", "valid test")
        .put("catalogNodeId", "GeoServiceAdminController")
        .put("url", "https://snapshot.tailormap.nl/geoserver/wms")
        .toPrettyString();

    MvcResult result = mockMvc.perform(post(adminBasePath + "/geo-services/new")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8)
            .content(geoServicePOSTBody))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", containsString("http://localhost/api/admin/geo-services/")))
        .andReturn();

    assertNotNull(result.getResponse().getRedirectedUrl());
    String serviceId = result.getResponse()
        .getRedirectedUrl()
        .substring(result.getResponse().getRedirectedUrl().lastIndexOf("/") + 1);

    // check that the created service is added to the catalog
    Catalog catalog = catalogRepository.findById(Catalog.MAIN).orElseThrow();
    CatalogNode attachedTo = catalog.getNodes().stream()
        .filter(node -> node.getId().equals("GeoServiceAdminController"))
        .findFirst()
        .orElseThrow();

    TailormapObjectRef objectRef = attachedTo.getItems().stream()
        .filter(item -> {
          logger.info(
              "Checking item with ID: {} {} (serviceId: {})", item.getId(), item.getKind(), serviceId);
          return serviceId.equals(item.getId());
        })
        .findFirst()
        .orElseThrow();

    assertEquals(serviceId, objectRef.getId());
    assertEquals(TailormapObjectRef.KindEnum.GEO_SERVICE, objectRef.getKind());
  }
}

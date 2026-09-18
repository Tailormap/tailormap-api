package org.tailormap.api.controller.admin;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
@PostgresIntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class FeatureSourceAdminControllerIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private CatalogRepository catalogRepository;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  private String getFeatureSourcePOSTBody(
      int port, String host, String database, String user, String password, String catalogNodeId) {
    return """
{
"title": "My Test Source",
"protocol": "JDBC",
"url": "",
"refreshCapabilities": true,
"jdbcConnection": {
"dbtype": "postgis",
"port": %s,
"host": "%s",
"database": "%s",
"schema": "public"
},
"authentication": {
"method": "password",
"username": "%s",
"password": "%s"
},
"catalogNodeId": "%s"
}""".formatted(port, host, database, user, password, catalogNodeId);
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void create_and_refresh_jdbc_feature_source_capabilities() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs
    final int expectedTotal = 39;

    String host = "localhost";
    int port = 54322;
    String database = "geodata";
    String user = "geodata";
    String password = "980f1c8A-25933b2";

    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl("jdbc:postgresql://%s:%s/%s".formatted(host, port, database));
    dataSource.setUsername(user);
    dataSource.setPassword(password);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("drop table if exists test");

    MvcResult result = mockMvc.perform(post(adminBasePath + "/feature-sources/new")
            .contentType(MediaType.APPLICATION_JSON)
            .content(getFeatureSourcePOSTBody(
                port, host, database, user, password, "FeatureSourceAdminController")))
        .andExpect(status().isCreated())
        .andExpect(redirectedUrlPattern("**/feature-sources/*"))
        .andReturn();

    assertNotNull(result.getResponse().getRedirectedUrl());
    String fsId = result.getResponse()
        .getRedirectedUrl()
        .substring(result.getResponse().getRedirectedUrl().lastIndexOf("/") + 1);

    // check that the created service is added to the catalog
    Catalog catalog = catalogRepository.findById(Catalog.MAIN).orElseThrow();
    CatalogNode attachedTo = catalog.getNodes().stream()
        .filter(node -> node.getId().equals("FeatureSourceAdminController"))
        .findFirst()
        .orElseThrow();

    TailormapObjectRef objectRef = attachedTo.getItems().stream()
        .filter(item -> fsId.equals(item.getId()))
        .findFirst()
        .orElseThrow();

    assertEquals(fsId, objectRef.getId());
    assertEquals(TailormapObjectRef.KindEnum.FEATURE_SOURCE, objectRef.getKind());

    result = mockMvc.perform(get(result.getResponse().getRedirectedUrl()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.allFeatureTypes").isArray())
        .andExpect(jsonPath("$.allFeatureTypes.length()").value(expectedTotal))
        .andExpect(jsonPath("$.protocol").value("JDBC"))
        .andReturn();

    Integer featureSourceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    String selfLink = JsonPath.read(result.getResponse().getContentAsString(), "$._links.self.href");

    try {
      jdbcTemplate.execute("create table test(id serial primary key)");

      mockMvc.perform(post(adminBasePath + "/feature-sources/%s/refresh-capabilities".formatted(featureSourceId)))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", equalTo(selfLink)));

      mockMvc.perform(get(selfLink).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.allFeatureTypes").isArray())
          .andExpect(jsonPath("$.allFeatureTypes.length()").value(expectedTotal + 1))
          .andExpect(jsonPath("$.protocol").value("JDBC"))
          .andExpect(jsonPath("$.allFeatureTypes[?(@.name=='test')]").isNotEmpty());
    } finally {
      try {
        new JdbcTemplate(dataSource).execute("drop table test");
      } catch (Exception e) {
        // ignore
      }
    }

    mockMvc.perform(post(adminBasePath + "/feature-sources/%s/refresh-capabilities".formatted(featureSourceId)))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", equalTo(selfLink)));

    mockMvc.perform(get(selfLink).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.allFeatureTypes").isArray())
        .andExpect(jsonPath("$.allFeatureTypes.length()").value(expectedTotal))
        .andExpect(jsonPath("$.protocol").value("JDBC"))
        .andExpect(jsonPath("$.allFeatureTypes[?(@.name=='test')]").isEmpty());
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void create_feature_source_with_invalid_catalog_node_id() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    String host = "localhost";
    int port = 54322;
    String database = "geodata";
    String user = "geodata";
    String password = "980f1c8A-25933b2";

    mockMvc.perform(post(adminBasePath + "/feature-sources/new")
            .contentType(MediaType.APPLICATION_JSON)
            .content(getFeatureSourcePOSTBody(port, host, database, user, password, "invalid")))
        .andDo(print())
        .andExpect(status().isNotFound());
  }
}

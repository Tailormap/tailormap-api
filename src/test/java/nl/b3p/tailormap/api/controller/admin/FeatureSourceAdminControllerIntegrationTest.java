package nl.b3p.tailormap.api.controller.admin;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Group;
import org.junit.jupiter.api.Test;
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

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
@PostgresIntegrationTest
class FeatureSourceAdminControllerIntegrationTest {
  @Autowired private WebApplicationContext context;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void refreshJdbcFeatureSourceCapabilities() throws Exception {
    MockMvc mockMvc =
        MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    String host = "localhost";
    int port = 54322;
    String database = "geodata";
    String user = "geodata";
    String password = "980f1c8A-25933b2";

    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(String.format("jdbc:postgresql://%s:%s/%s", host, port, database));
    dataSource.setUsername(user);
    dataSource.setPassword(password);
    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("drop table if exists test");

    String featureSourcePOSTBody =
        String.format(
            """
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
                          }
                        }""",
            port, host, database, user, password);

    MvcResult result =
        mockMvc
            .perform(
                post(adminBasePath + "/feature-sources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(featureSourcePOSTBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.allFeatureTypes").isArray())
            .andExpect(jsonPath("$.allFeatureTypes.length()").value(30))
            .andReturn();
    Integer featureSourceId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    String selfLink =
        JsonPath.read(result.getResponse().getContentAsString(), "$._links.self.href");

    try {
      jdbcTemplate.execute("create table test(id serial primary key)");

      mockMvc
          .perform(
              post(
                  adminBasePath
                      + String.format("/feature-sources/%s/refresh-capabilities", featureSourceId)))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", equalTo(selfLink)));

      mockMvc
          .perform(get(selfLink).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.allFeatureTypes").isArray())
          .andExpect(jsonPath("$.allFeatureTypes.length()").value(31))
          .andExpect(jsonPath("$.allFeatureTypes[?(@.name=='test')]").isNotEmpty());
    } finally {
      try {
        new JdbcTemplate(dataSource).execute("drop table test");
      } catch (Exception ignored) {
      }
    }

    mockMvc
        .perform(
            post(
                adminBasePath
                    + String.format("/feature-sources/%s/refresh-capabilities", featureSourceId)))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", equalTo(selfLink)));

    mockMvc
        .perform(get(selfLink).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.allFeatureTypes").isArray())
        .andExpect(jsonPath("$.allFeatureTypes.length()").value(30))
        .andExpect(jsonPath("$.allFeatureTypes[?(@.name=='test')]").isEmpty());
  }
}

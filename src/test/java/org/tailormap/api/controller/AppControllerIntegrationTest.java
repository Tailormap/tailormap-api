/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.repository.ConfigurationRepository;

@AutoConfigureMockMvc
@PostgresIntegrationTest
class AppControllerIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired ConfigurationRepository configurationRepository;

  @Autowired EntityManager entityManager;

  @Value("${tailormap-api.base-path}")
  private String basePath;

  @Test
  void returns_default_when_no_arguments() throws Exception {
    mockMvc
        .perform(get(basePath + "/app").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.kind").value("app"))
        .andExpect(jsonPath("$.name").value("default"))
        .andExpect(jsonPath("$.title").value("Tailormap demo"))
        .andExpect(jsonPath("$.components").isArray())
        .andExpect(jsonPath("$.components.length()").value(2))
        .andExpect(jsonPath("$.components[0].type").value("EDIT"))
        .andExpect(jsonPath("$.components[0].config.enabled").value(true))
        .andExpect(jsonPath("$.styling.primaryColor").isEmpty())
        .andExpect(
            jsonPath(
                "$.styling.logo",
                matchesPattern(
                    "^http://localhost/api/uploads/app-logo/[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}/gradient\\.svg$")));
  }

  @Test
  /* this test changes database content but reverses it after the test */
  @Transactional
  void not_found_when_no_default() throws Exception {
    Configuration defaultApp =
        configurationRepository.findByKey(Configuration.DEFAULT_APP).orElseThrow();
    entityManager.remove(defaultApp);
    mockMvc
        .perform(get(basePath + "/app").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
    Configuration defaultAppConfig = new Configuration();
    defaultAppConfig.setKey(Configuration.DEFAULT_APP);
    defaultAppConfig.setValue(defaultApp.getValue());
    entityManager.persist(defaultAppConfig);
  }

  @Test
  /* this test changes database content */
  @Transactional
  void not_found_when_default_not_exists() throws Exception {
    Configuration defaultApp =
        configurationRepository.findByKey(Configuration.DEFAULT_APP).orElseThrow();
    defaultApp.setValue("non existing app!");
    mockMvc
        .perform(get(basePath + "/app").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
    defaultApp.setValue("default");
  }

  @Test
  void finds_by_name() throws Exception {
    String path = basePath + "/app/default";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.kind").value("app"))
        .andExpect(jsonPath("$.name").value("default"));
  }

  @Test
  void not_found_by_name() throws Exception {
    String path = basePath + "/app/waldo";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  void finds_service_viewer() throws Exception {
    String path = basePath + "/service/snapshot-geoserver";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.kind").value("service"))
        .andExpect(jsonPath("$.name").value("snapshot-geoserver"))
        .andExpect(jsonPath("$.title").value("Test GeoServer"));
  }

  @Test
  void not_found_unpublished_service_viewer() throws Exception {
    String path = basePath + "/service/openbasiskaart";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  void should_send_401_when_application_configured() throws Exception {
    String path = basePath + "/app/secured";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }
}

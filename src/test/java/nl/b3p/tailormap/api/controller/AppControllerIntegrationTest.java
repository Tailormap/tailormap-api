/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@PostgresIntegrationTest
class AppControllerIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired ConfigurationRepository configurationRepository;

  private String getApiVersionFromPom() {
    String apiVersion = System.getenv("API_VERSION");
    assumeFalse(null == apiVersion, "API version unknown, should be set in environment");
    return apiVersion;
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void returns_default_when_no_arguments() throws Exception {
    mockMvc
        .perform(get("/app").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("default"))
        .andExpect(jsonPath("$.lang").value("nl_NL"))
        .andExpect(jsonPath("$.title").value("Tailormap demo"));
    //        .andExpect(jsonPath("$.components").isArray())
    //        .andExpect(jsonPath("$.components[0].type").value("measure"))
    //        .andExpect(jsonPath("$.styling.primaryColor").isEmpty())
    //        .andExpect(jsonPath("$.styling.logo").isEmpty());
  }

  @Test
  /* this test changes database content */
  @Transactional
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void not_found_when_no_default() throws Exception {
    Configuration defaultApp = configurationRepository.findByKey(Configuration.DEFAULT_APP).get();
    configurationRepository.delete(defaultApp);
    mockMvc
        .perform(get("/app").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("No default application configured"));
    configurationRepository.save(defaultApp);
  }

  @Test
  /* this test changes database content */
  @Transactional
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void not_found_when_default_not_exists() throws Exception {
    Configuration defaultApp = configurationRepository.findByKey(Configuration.DEFAULT_APP).get();
    defaultApp.setValue("non existing app!");
    configurationRepository.save(defaultApp);
    mockMvc
            .perform(get("/app").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("Default application not found"));
    defaultApp.setValue("default");
    configurationRepository.save(defaultApp);
  }


  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void finds_by_name() throws Exception {
    mockMvc
        .perform(get("/app").param("name", "default").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("default"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void not_found_by_name() throws Exception {
    mockMvc
        .perform(get("/app").param("name", "waldo").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Application \"waldo\" not found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void finds_by_id() throws Exception {
    mockMvc
        .perform(get("/app").param("id", "1").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.name").value("default"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void not_found_by_id() throws Exception {
    mockMvc
        .perform(get("/app").param("appId", "-9000").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Application with id -9000 not found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void bad_request_when_both_parameters() throws Exception {
    mockMvc
        .perform(
            get("/app")
                .param("appId", "100")
                .param("name", "test")
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON));
  }

  //  @Test
  //  /* this test changes database content */
  //  @Transactional
  //  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  //  void should_return_default_lang_when_application_language_not_configured() throws Exception {
  //    // unset language
  //    applicationRepository.getReferenceById(1L).setLang(null);
  //
  //    mockMvc
  //        .perform(get("/app").param("appId", "1").accept(MediaType.APPLICATION_JSON))
  //        .andExpect(status().isOk())
  //        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
  //        .andExpect(jsonPath("$.apiVersion").value(getApiVersionFromPom()))
  //        .andExpect(jsonPath("$.id").value(1))
  //        .andExpect(jsonPath("$.name").value("test"))
  //        .andExpect(jsonPath("$.version").value(1))
  //        .andExpect(jsonPath("$.title").value("test title"))
  //        // expect default value
  //        .andExpect(jsonPath("$.lang").value("nl_NL"))
  //        .andExpect(jsonPath("$.components").isArray())
  //        .andExpect(jsonPath("$.components[0].type").value("measure"))
  //        .andExpect(jsonPath("$.styling.primaryColor").isEmpty())
  //        .andExpect(jsonPath("$.styling.logo").isEmpty());
  //  }

  //  @Test
  //  /* this test changes database content */
  //  @Order(Integer.MAX_VALUE - 1)
  //  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  //  void should_send_401_when_application_configured() throws Exception {
  //    applicationRepository.setAuthenticatedRequired(1L, true);
  //
  //    mockMvc
  //        .perform(get("/app").param("appId", "1").accept(MediaType.APPLICATION_JSON))
  //        .andExpect(status().isUnauthorized())
  //        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
  //        .andExpect(jsonPath("$.code").value(401))
  //        .andExpect(jsonPath("$.url").value("/login"));
  //  }
}

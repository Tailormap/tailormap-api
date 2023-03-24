/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.persistence.EntityManager;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.repository.ConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@PostgresIntegrationTest
class AppControllerIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired ConfigurationRepository configurationRepository;

  @Autowired EntityManager entityManager;

  @Value("${tailormap-api.base-path}")
  private String basePath;

  // Required for AppRestControllerAdvice.populateViewerKind()
  private static RequestPostProcessor requestPostProcessor(String servletPath) {
    return request -> {
      request.setServletPath(servletPath);
      return request;
    };
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void returns_default_when_no_arguments() throws Exception {
    mockMvc
        .perform(get(basePath + "/app").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.kind").value("app"))
        .andExpect(jsonPath("$.name").value("default"))
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
    Configuration defaultApp = configurationRepository.findByKey(Configuration.DEFAULT_APP).orElseThrow();
    entityManager.remove(defaultApp);
    mockMvc
        .perform(get(basePath + "/app").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
    entityManager.persist(defaultApp);
  }

  @Test
  /* this test changes database content */
  @Transactional
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void not_found_when_default_not_exists() throws Exception {
    Configuration defaultApp = configurationRepository.findByKey(Configuration.DEFAULT_APP).orElseThrow();
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
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void finds_by_name() throws Exception {
    String path = basePath + "/app/default";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.kind").value("app"))
        .andExpect(jsonPath("$.name").value("default"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void not_found_by_name() throws Exception {
    String path = basePath + "/app/waldo";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void finds_service_viewer() throws Exception {
    String path = basePath + "/service/snapshot-geoserver";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.kind").value("service"))
        .andExpect(jsonPath("$.name").value("snapshot-geoserver"))
        .andExpect(jsonPath("$.title").value("Test GeoServer"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void not_found_unpublished_service_viewer() throws Exception {
    String path = basePath + "/service/openbasiskaart";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
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
  //        .perform(get(basePath + "/app").param("appId", "1").accept(MediaType.APPLICATION_JSON))
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
  //        .perform(get(basePath + "/app").param("appId", "1").accept(MediaType.APPLICATION_JSON))
  //        .andExpect(status().isUnauthorized())
  //        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
  //        .andExpect(jsonPath("$.code").value(401))
  //        .andExpect(jsonPath("$.url").value("/login"));
  //  }
}

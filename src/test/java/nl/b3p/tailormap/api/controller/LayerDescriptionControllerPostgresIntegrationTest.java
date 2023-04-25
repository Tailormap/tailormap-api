/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Issue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class LayerDescriptionControllerPostgresIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  private static RequestPostProcessor requestPostProcessor(String servletPath) {
    return request -> {
      request.setServletPath(servletPath);
      return request;
    };
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void app_not_found_404() throws Exception {
    final String path = apiBasePath + "/app/1234/layer/76/describe";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void public_app() throws Exception {
    final String path =
        apiBasePath
            + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/describe";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.featureTypeName").value("begroeidterreindeel"))
        .andExpect(jsonPath("$.geometryAttribute").value("geom"))
        .andExpect(jsonPath("$.id").value("lyr:snapshot-geoserver:postgis:begroeidterreindeel"))
        .andExpect(jsonPath("$.serviceId").value("snapshot-geoserver"))
        .andExpect(jsonPath("$.attributes").isArray())
        .andExpect(
            jsonPath("$.attributes[?(@.name == 'relatievehoogteligging')].type").value("integer"));
  }

  @Disabled("This test fails, proxying is currently not working/non-existent")
  @Issue("https://b3partners.atlassian.net/browse/HTM-714")
  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(username = "noproxyuser")
  void test_wms_secured_app_denied() throws Exception {
    final String path = apiBasePath + "/app/default/layer/19/describe";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string("{\"code\":401,\"url\":\"/login\"}"));
  }

  @Disabled("This test fails, proxying is currently not working/non-existent")
  @Issue("https://b3partners.atlassian.net/browse/HTM-714")
  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "proxyuser",
      authorities = {"ProxyGroup"})
  void test_wms_secured_app_granted_but_no_feature_type() throws Exception {
    mockMvc
        .perform(get("/app/6/layer/21/describe"))
        .andExpect(status().isNotFound())
        .andExpect(content().string("Layer does not have feature type"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @Disabled("TODO: test app was removed from integration dataset, restore later")
  void handles_unknown_attribute_type() throws Exception {
    // Depends on external service, may fail/change
    mockMvc
        .perform(get("/app/7/layer/24/describe"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.attributes").isArray())
        .andExpect(jsonPath("$.attributes.length()").value(9))
        .andReturn();
  }
}

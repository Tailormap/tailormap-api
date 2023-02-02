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

import nl.b3p.tailormap.api.JPAConfiguration;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.security.SecurityConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = {
      JPAConfiguration.class,
      LayerDescriptionController.class,
      SecurityConfig.class,
      AuthorizationService.class,
      AppRestControllerAdvice.class
    })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("postgresql")
@Execution(ExecutionMode.CONCURRENT)
class LayerDescriptionControllerPostgresIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void app_not_found_404() throws Exception {
    mockMvc
        .perform(get("/app/1234/layer/76/describe"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Application with id 1234 not found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void public_app() throws Exception {
    mockMvc
        .perform(get("/app/1/layer/6/describe"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.featureTypeName").value("begroeidterreindeel"))
        .andExpect(jsonPath("$.geometryAttribute").value("geom"))
        .andExpect(jsonPath("$.id").value("6"))
        .andExpect(jsonPath("$.serviceId").value(6))
        .andExpect(jsonPath("$.attributes").isArray())
        .andExpect(jsonPath("$.attributes[?(@.id == 22)].name").value("relatievehoogteligging"))
        .andExpect(jsonPath("$.attributes[?(@.id == 22)].type").value("integer"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(username = "noproxyuser")
  void test_wms_secured_app_denied() throws Exception {
    mockMvc
        .perform(get("/app/6/layer/19/describe"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string("{\"code\":401,\"url\":\"/login\"}"));
  }

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

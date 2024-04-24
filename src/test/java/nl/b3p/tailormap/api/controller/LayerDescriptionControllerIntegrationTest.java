/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static nl.b3p.tailormap.api.TestRequestProcessor.setServletPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class LayerDescriptionControllerIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void app_not_found_404() throws Exception {
    final String path = apiBasePath + "/app/1234/layer/76/describe";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
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
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.featureTypeName").value("begroeidterreindeel"))
        .andExpect(jsonPath("$.geometryAttribute").value("geom"))
        .andExpect(jsonPath("$.id").value("lyr:snapshot-geoserver:postgis:begroeidterreindeel"))
        .andExpect(jsonPath("$.serviceId").value("snapshot-geoserver"))
        .andExpect(jsonPath("$.attributes").isArray())
        .andExpect(
            jsonPath("$.attributes[?(@.key == 'relatievehoogteligging')].type").value("integer"))
        .andExpectAll(
            jsonPath("$.attributes[?(@.key == 'terminationdate')]").doesNotExist(),
            jsonPath("$.attributes[?(@.key == 'geom_kruinlijn')]").doesNotExist())
        .andExpect(jsonPath("$.attributes[?(@.key == 'gmlid')].nullable").value(false))
        .andExpect(jsonPath("$.attributes[?(@.key == 'gmlid')].editable").value(false))
        .andExpect(jsonPath("$.editable").value(true));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void appLayer_with_feature_type_but_no_editable_setting() throws Exception {
    final String path =
        apiBasePath
            + "/app/default/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/describe";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.featureTypeName").value("begroeidterreindeel"))
        .andExpect(jsonPath("$.geometryAttribute").value("geom"))
        .andExpect(
            jsonPath("$.id").value("lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel"))
        .andExpect(jsonPath("$.serviceId").value("snapshot-geoserver-proxied"))
        .andExpect(jsonPath("$.attributes").isArray())
        .andExpect(
            jsonPath("$.attributes[?(@.key == 'relatievehoogteligging')].type").value("integer"))
        .andExpect(jsonPath("$.attributes[?(@.key == 'gmlid')].nullable").value(false))
        .andExpect(jsonPath("$.attributes[?(@.key == 'gmlid')].editable").value(false))
        .andExpect(jsonPath("$.editable").value(false));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test_wms_secured_app_denied() throws Exception {
    final String path =
        apiBasePath
            + "/app/secured/layer/lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel/describe";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void test_wms_secured_app_granted_but_no_feature_type() throws Exception {
    final String path =
        apiBasePath
            + "/app/secured/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/describe";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Layer does not have feature type"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "foo",
      authorities = {"test-foo"})
  void test_authorization_service_allow_but_layer_deny() throws Exception {
    final String bgtPath =
        apiBasePath + "/app/secured-auth/layer/lyr:filtered-snapshot-geoserver:BGT/describe";

    mockMvc
        .perform(get(bgtPath).accept(MediaType.APPLICATION_JSON).with(setServletPath(bgtPath)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "foo",
      authorities = {"test-foo"})
  void test_authorization_service_allow_and_layer_allow() throws Exception {

    final String begroeidterreindeelPath =
        apiBasePath
            + "/app/secured-auth/layer/lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel/describe";

    mockMvc
        .perform(
            get(begroeidterreindeelPath)
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(begroeidterreindeelPath)))
        .andExpect(status().isNotFound());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "foo",
      authorities = {"test-foo", "test-baz"})
  void test_authorization_application_layer_authorization_conflicting_allow_deny()
      throws Exception {
    final String bgtPath =
        apiBasePath + "/app/secured-auth/layer/lyr:filtered-snapshot-geoserver:BGT/describe";

    mockMvc
        .perform(get(bgtPath).accept(MediaType.APPLICATION_JSON).with(setServletPath(bgtPath)))
        .andExpect(status().isNotFound());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "foo",
      authorities = {"test-bar"})
  void test_authorization_access_to_layer_but_not_application() throws Exception {
    final String bgtPath =
        apiBasePath + "/app/secured-auth/layer/lyr:filtered-snapshot-geoserver:BGT/describe";

    mockMvc
        .perform(get(bgtPath).accept(MediaType.APPLICATION_JSON).with(setServletPath(bgtPath)))
        .andExpect(status().isUnauthorized());
  }
}

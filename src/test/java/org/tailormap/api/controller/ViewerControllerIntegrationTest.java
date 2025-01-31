/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.repository.ApplicationRepository;
import org.tailormap.api.viewer.model.Service;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ViewerControllerIntegrationTest {
  @Autowired
  ApplicationRepository applicationRepository;

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void services_should_be_unique() throws Exception {
    // GET https://snapshot.tailormap.nl/api/app/default/map
    final String path = apiBasePath + "/app/default/map";
    MvcResult result = mockMvc.perform(
            get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.initialExtent").isMap())
        .andExpect(jsonPath("$.maxExtent").isMap())
        .andExpect(jsonPath("$.services").isArray())
        .andExpect(jsonPath("$.crs.code").value("EPSG:28992"))
        .andReturn();

    String body = result.getResponse().getContentAsString();
    List<Service> allSvc = JsonPath.read(body, "$.services");
    Set<Service> uniqueSvc = new HashSet<>(allSvc);
    assertTrue(1 < allSvc.size(), "there must be more than one service, otherwise change the url");
    assertEquals(allSvc.size(), uniqueSvc.size(), () -> ("services array contains non-unique items: " + allSvc));
  }

  @Test
  void should_error_when_calling_with_nonexistent_id() throws Exception {
    final String path = apiBasePath + "/app/400/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  void should_not_find_when_called_without_id() throws Exception {
    final String path = apiBasePath + "/app/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isNotFound());
  }

  @Test
  void should_contain_proxy_url() throws Exception {
    String path = apiBasePath + "/app/default/map";
    mockMvc.perform(get(path).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.services[?(@.id == 'snapshot-geoserver-proxied')].url")
            .value(contains(nullValue())))
        .andExpect(
            jsonPath(
                    "$.appLayers[?(@.id === 'lyr:snapshot-geoserver-proxied:postgis:begroeidterreindeel')].url")
                // Need to use contains() because jsonPath() returns an array even
                // when the expression resolves to a single scalar property
                .value(
                    contains(
                        endsWith(
                            "/app/default/layer/lyr%3Asnapshot-geoserver-proxied%3Apostgis%3Abegroeidterreindeel/proxy/wms"))))
        .andExpect(jsonPath("$.services[?(@.id == 'pdok-kadaster-bestuurlijkegebieden')].url")
            .value(contains(nullValue())))
        .andExpect(
            jsonPath(
                    "$.appLayers[?(@.id === 'lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied')].url")
                .value(
                    contains(
                        endsWith(
                            "/app/default/layer/lyr%3Apdok-kadaster-bestuurlijkegebieden%3AProvinciegebied/proxy/wms"))))

    // Backend does not save legendImageUrl from capabilities, and therefore also does not
    // replace it with a proxied version. Frontend will use normal URL to create standard WMS
    // GetLegendGraphic request, which usually works fine
    // Old 10.0 code:
    // https://github.com/Tailormap/tailormap-api/blob/tailormap-api-10.0.0/src/main/java/nl/b3p/tailormap/api/controller/MapController.java#L440
    //        .andExpect(
    //            jsonPath("$.appLayers[?(@.id ===
    // 'lyr:pdok-kadaster-bestuurlijkegebieden:Provinciegebied')].legendImageUrl")
    //                .value(
    //                    contains(
    //                        allOf(
    //
    // containsString("/app/default/layer/lyr%3Apdok-kadaster-bestuurlijkegebieden%3AProvinciegebied/proxy/wms"),
    //                            containsString("request=GetLegendGraphic")))))
    ;
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_contain_proxied_secured_service_layer() throws Exception {
    final String path = apiBasePath + "/app/secured/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.appLayers[?(@.id == 'lyr:openbasiskaart-proxied:osm')]")
            .exists())
        .andExpect(jsonPath("$.services[?(@.id == 'openbasiskaart-proxied')]")
            .exists());
  }

  @Test
  void should_not_contain_proxied_secured_service_layer_on_public_app() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.appLayers[?(@.id == 'lyr:openbasiskaart-proxied:osm')]")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.services[?(@.id == 'openbasiskaart-proxied')]")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.appLayers[?(@.id == 'lyr:bestuurlijkegebieden-proxied:Provinciegebied')]")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.services[?(@.id == 'bestuurlijkegebieden-proxied')]")
            .doesNotHaveJsonPath());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void should_not_contain_proxied_secured_service_layer_on_public_app_even_when_authorized() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.appLayers[?(@.id == 'lyr:openbasiskaart-proxied:osm')]")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.services[?(@.id == 'openbasiskaart-proxied')]")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.appLayers[?(@.id == 'lyr:bestuurlijkegebieden-proxied:Provinciegebied')]")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.services[?(@.id == 'bestuurlijkegebieden-proxied')]")
            .doesNotHaveJsonPath());
  }

  @Test
  void should_contain_description() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            // Application layer description
            jsonPath(
                    "$.appLayers[?(@.id === 'lyr:snapshot-geoserver:postgis:begroeidterreindeel')].description")
                .value(contains(startsWith("This layer shows data from https://www.postgis.net"))));
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void should_return_data_for_configured_app() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    final String appLayerLufoPath = "$.appLayers[?(@.id == 'lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR')]";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.initialExtent").isMap())
        .andExpect(jsonPath("$.maxExtent").isMap())
        .andExpect(jsonPath("$.services").isArray())
        .andExpect(jsonPath("$.appLayers").isArray())
        .andExpect(jsonPath("$.appLayers[0]").isMap())
        // Note: if the testdata was created with MAP5_URL set, the appLayers array will have 4 more
        // layers
        .andExpect(jsonPath("$.appLayers.length()").value(15))
        .andExpect(jsonPath("$.appLayers[?(@.id == 'lyr:openbasiskaart-tms:xyz')].hasAttributes")
            .value(false))
        .andExpect(jsonPath("$.appLayers[?(@.id == 'lyr:b3p-mapproxy-luchtfoto:xyz')].hasAttributes")
            .value(false))
        .andExpect(jsonPath(appLayerLufoPath).exists())
        .andExpect(jsonPath(appLayerLufoPath + "[0].legendImageUrl").isEmpty())
        .andExpect(jsonPath(appLayerLufoPath + ".visible").value(false))
        .andExpect(jsonPath(appLayerLufoPath + "[0].minScale").isEmpty())
        .andExpect(jsonPath(appLayerLufoPath + "[0].maxScale").isEmpty())
        .andExpect(jsonPath(appLayerLufoPath + "[0].hiDpiMode").isEmpty())
        .andExpect(
            jsonPath(appLayerLufoPath + "[0].hiDpiSubstituteLayer").isEmpty())
        .andExpect(jsonPath("$.crs.code").value("EPSG:28992"));
  }

  @Test
  void should_deny_showing_secured_application() throws Exception {
    final String path = apiBasePath + "/app/secured/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(username = "foo")
  void should_allow_showing_secured_application_authenticated() throws Exception {
    final String path = apiBasePath + "/app/secured/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(
      username = "foo",
      authorities = {"test-foo"})
  void should_allow_showing_filtered_application_authenticated() throws Exception {
    final String path = apiBasePath + "/app/secured-auth/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(
      username = "foo",
      authorities = {"test-baz"})
  void should_deny_showing_filtered_application_with_wrong_group() throws Exception {
    final String path = apiBasePath + "/app/secured-auth/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "foo",
      authorities = {"test-foo"})
  void should_filter_layers() throws Exception {
    final String path = apiBasePath + "/app/secured-auth/map";
    // the group "test-foo" has READ permissions to the Application and the GeoService, and is
    // denied access to the filtered "BGT" layer.
    // (postgis:begroeidterreindeel has no explicit rule, so it is allowed.)
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.services.length()").value(2))
        .andExpect(jsonPath("$.appLayers.length()").value(2))
        .andExpect(jsonPath("$.appLayers[0].id")
            .value("lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel"))
        .andExpect(jsonPath("$.appLayers[1].id").value("lyr:snapshot-geoserver:BGT"));
  }

  @Test
  @WithMockUser(
      username = "foo",
      authorities = {"test-foo", "test-baz"})
  void should_not_filter_layers_in_correct_group() throws Exception {
    // the group "test-baz" has READ permissions to the GeoService and the filtered "BGT" layer.
    final String path = apiBasePath + "/app/secured-auth/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.services.length()").value(2))
        .andExpect(jsonPath("$.appLayers.length()").value(3))
        .andExpect(jsonPath("$.appLayers[0].id").value("lyr:filtered-snapshot-geoserver:BGT"))
        .andExpect(jsonPath("$.appLayers[1].id")
            .value("lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel"))
        .andExpect(jsonPath("$.appLayers[2].id").value("lyr:snapshot-geoserver:BGT"));
  }

  @Test
  @WithMockUser(
      username = "foo",
      authorities = {"admin"})
  void admin_can_see_anything() throws Exception {
    final String path = apiBasePath + "/app/secured-auth/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.services.length()").value(2))
        .andExpect(jsonPath("$.appLayers.length()").value(3))
        .andExpect(jsonPath("$.appLayers[0].id").value("lyr:filtered-snapshot-geoserver:BGT"))
        .andExpect(jsonPath("$.appLayers[1].id")
            .value("lyr:filtered-snapshot-geoserver:postgis:begroeidterreindeel"))
        .andExpect(jsonPath("$.appLayers[2].id").value("lyr:snapshot-geoserver:BGT"));
  }

  @Test
  @WithMockUser(
      username = "foo",
      authorities = {"test-bar"})
  void should_filter_services() throws Exception {
    // the group "test-bar" has READ permissions to the Application, but not the GeoService.
    final String path = apiBasePath + "/app/secured-auth/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.services.length()").value(1))
        .andExpect(jsonPath("$.appLayers.length()").value(1))
        .andExpect(jsonPath("$.appLayers[0].id").value("lyr:snapshot-geoserver:BGT"));
  }

  @Test
  void should_send_401_when_application_login_required() throws Exception {
    String path = apiBasePath + "/app/secured/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  void should_contain_terrain_layers() throws Exception {
    final String path = apiBasePath + "/app/3d_utrecht/map";
    mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON).with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.terrainLayerTreeNodes[0].id").value("root-terrain-layers"))
        .andExpect(jsonPath("$.terrainLayerTreeNodes[1].id").value("lyr:ahn_terrain_model:quantizedmesh"));
  }
}

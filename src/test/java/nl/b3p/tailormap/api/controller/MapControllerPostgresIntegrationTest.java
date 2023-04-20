/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.viewer.model.Service;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.Issue;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MapControllerPostgresIntegrationTest {
  @Autowired ApplicationRepository applicationRepository;
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
  void services_should_be_unique() throws Exception {
    // GET https://snapshot.tailormap.nl/api/app/default/map
    final String path = apiBasePath + "/app/default/map";
    MvcResult result =
        mockMvc
            .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
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
    assertEquals(
        allSvc.size(),
        uniqueSvc.size(),
        () -> ("services array contains non-unique items: " + allSvc));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_error_when_calling_with_nonexistent_id() throws Exception {
    final String path = apiBasePath + "/app/400/map";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Not Found"));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_not_find_when_called_without_id() throws Exception {
    final String path = apiBasePath + "/app/map";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isNotFound());
  }

  @Test
  @Disabled("This test fails, proxying is currently not working/non-existent")
  @Issue("https://b3partners.atlassian.net/browse/HTM-714")
  // TODO fix this test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_contain_proxy_url() throws Exception {
    mockMvc
        .perform(get("/app/5/map"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$.services[?(@.name == 'Bestuurlijke Gebieden View Service (proxied)')].url")
                .value(contains(nullValue())))
        .andExpect(
            jsonPath("$.appLayers[?(@.layerName === \"Gemeentegebied\")].url")
                // Need to use contains() because jsonPath() returns an array even
                // when the expression resolves to a single scalar property
                .value(contains(endsWith("/app/5/layer/15/proxy/wms"))))
        .andExpect(
            jsonPath("$.services[?(@.name == 'PDOK HWH luchtfoto (proxied)')].url")
                .value(contains(nullValue())))
        .andExpect(
            jsonPath("$.appLayers[?(@.layerName === \"Actueel_ortho25\")].url")
                .value(contains(endsWith("/app/5/layer/16/proxy/wmts"))))
        .andExpect(
            jsonPath("$.appLayers[?(@.layerName === \"Gemeentegebied\")].legendImageUrl")
                .value(
                    contains(
                        allOf(
                            containsString("/app/5/layer/15/proxy/wms"),
                            containsString("request=GetLegendGraphic")))))
        .andReturn();
  }

  @Test
  @Disabled("This test fails, proxying is currently not working/non-existent")
  @Issue("https://b3partners.atlassian.net/browse/HTM-714")
  // TODO fix this test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_not_contain_proxied_secured_service_layer() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.services[?(@.name == 'Beveiligde proxy WMS')]").doesNotExist())
        .andExpect(jsonPath("$.appLayers[?(@.layerName === \"Provinciegebied\")]").doesNotExist())
        .andReturn();
  }

  @Test
  @Disabled("This test fails, AppTreeLayerNode does not have a description property")
  @Issue("https://b3partners.atlassian.net/browse/HTM-744")
  // TODO fix this test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_contain_description() throws Exception {
    // GET https://snapshot.tailormap.nl/api/app/default/map
    final String path = apiBasePath + "/app/default/map";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            // Level description
            jsonPath("$.layerTreeNodes[?(@.name === 'gebieden')].description")
                .value("Enkele externe lagen van PDOK met WFS koppeling."))
        .andExpect(
            // Application layer description
            jsonPath("$.layerTreeNodes[?(@.name === 'begroeidterreindeel')].description")
                .value(
                    contains(startsWith("Deze laag toont gegevens uit http://www.postgis.net/"))))
        .andReturn();
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void should_return_data_for_configured_app() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    mockMvc
        .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.initialExtent").isMap())
        .andExpect(jsonPath("$.maxExtent").isMap())
        .andExpect(jsonPath("$.services").isArray())
        .andExpect(jsonPath("$.appLayers").isArray())
        .andExpect(jsonPath("$.appLayers[0]").isMap())
        .andExpect(jsonPath("$.appLayers.length()").value(9))
        .andExpect(jsonPath("$.appLayers[0].hasAttributes").value(false))
        .andExpect(jsonPath("$.appLayers[1].hasAttributes").value(false))
        .andExpect(jsonPath("$.appLayers[2].legendImageUrl").isEmpty())
        .andExpect(jsonPath("$.appLayers[2].visible").value(false))
        .andExpect(jsonPath("$.appLayers[2].minScale").isEmpty())
        .andExpect(jsonPath("$.appLayers[2].maxScale").isEmpty())
        .andExpect(jsonPath("$.appLayers[2].id").value("lyr:pdok-hwh-luchtfotorgb:Actueel_orthoHR"))
        .andExpect(jsonPath("$.appLayers[2].hiDpiMode").isEmpty())
        .andExpect(jsonPath("$.appLayers[2].hiDpiSubstituteLayer").isEmpty())
        .andExpect(jsonPath("$.crs.code").value("EPSG:28992"));
  }

  @Disabled("Authorization is not yet implemented")
  @Issue("https://b3partners.atlassian.net/browse/HTM-704")
  // TODO fix this test
  @Test
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void should_show_filtered_layer_tree() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    MvcResult result =
        mockMvc
            .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    Map<String, JSONObject> treeNodeMap = new HashMap<>();
    String body = result.getResponse().getContentAsString();
    JSONObject rootNode = null;

    JSONArray baseLayerTreeNodes = new JSONObject(body).getJSONArray("layerTreeNodes");
    for (int i = 0; i < baseLayerTreeNodes.length(); i++) {
      JSONObject node = (JSONObject) baseLayerTreeNodes.get(i);

      String id = node.getString("id");
      assertFalse(treeNodeMap.containsKey(id), String.format("node %s appears multiple times", id));
      treeNodeMap.put(id, node);

      if (node.getBoolean("root")) {
        assertNull(rootNode, "Root node already exists");
        rootNode = node;
      }
    }

    assertNotNull(rootNode, "no root node found");
    assertEquals(
        2, rootNode.getJSONArray("childrenIds").length(), "root node had wrong amount of children");

    JSONObject groenNode = treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(0));
    assertNotNull(groenNode, "first child of root is not valid");
    assertEquals(
        3,
        groenNode.getJSONArray("childrenIds").length(),
        "first node of root had wrong amount of children");
    assertEquals(
        2,
        treeNodeMap.get(groenNode.getJSONArray("childrenIds").get(0)).getInt("appLayerId"),
        "incorrect appLayerId for first child");
    assertEquals(
        3,
        treeNodeMap.get(groenNode.getJSONArray("childrenIds").get(1)).getInt("appLayerId"),
        "incorrect appLayerId for second child");
    assertEquals(
        4,
        treeNodeMap.get(groenNode.getJSONArray("childrenIds").get(2)).getInt("appLayerId"),
        "incorrect appLayerId for third child");

    JSONObject woonplaatsenNode =
        treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(1));
    assertNotNull(woonplaatsenNode, "second child of root is not valid");
    assertEquals(
        1,
        woonplaatsenNode.getJSONArray("childrenIds").length(),
        "second node of root had wrong amount of children");
    assertEquals(
        5,
        treeNodeMap.get(woonplaatsenNode.getJSONArray("childrenIds").get(0)).getInt("appLayerId"),
        "incorrect appLayerId for first child (of second child)");
  }

  @Disabled("Authorization is not yet implemented")
  @Issue("https://b3partners.atlassian.net/browse/HTM-704")
  // TODO fix this test
  @Test
  @WithMockUser(
      username = "admin",
      authorities = {"NotAdmin"})
  void should_show_filtered_layer_tree_unauthorized() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    MvcResult result =
        mockMvc
            .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    Map<String, JSONObject> treeNodeMap = new HashMap<>();
    String body = result.getResponse().getContentAsString();
    JSONObject rootNode = null;
    JSONArray baseLayerTreeNodes = new JSONObject(body).getJSONArray("layerTreeNodes");
    for (int i = 0; i < baseLayerTreeNodes.length(); i++) {
      JSONObject node = (JSONObject) baseLayerTreeNodes.get(i);
      String id = node.getString("id");
      assertFalse(treeNodeMap.containsKey(id), String.format("node %s appears multiple times", id));
      treeNodeMap.put(id, node);

      if (node.getBoolean("root")) {
        assertNull(rootNode, "Root node already exists");
        rootNode = node;
      }
    }

    assertNotNull(rootNode, "no root node found");
    assertEquals(
        2, rootNode.getJSONArray("childrenIds").length(), "root node had wrong amount of children");

    JSONObject groenNode = treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(0));
    assertNotNull(groenNode, "first child of root is not valid");
    assertEquals(
        0,
        groenNode.getJSONArray("childrenIds").length(),
        "first node of root had wrong amount of children");

    JSONObject woonplaatsenNode =
        treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(1));
    assertNotNull(woonplaatsenNode, "second child of root is not valid");
    assertEquals(
        1,
        woonplaatsenNode.getJSONArray("childrenIds").length(),
        "second node of root had wrong amount of children");
  }

  @Disabled("Authorization is not yet implemented")
  @Issue("https://b3partners.atlassian.net/browse/HTM-705")
  // TODO fix this test
  @Test
  void should_show_filtered_base_layer_tree() throws Exception {
    final String path = apiBasePath + "/app/default/map";
    MvcResult result =
        mockMvc
            .perform(get(path).accept(MediaType.APPLICATION_JSON).with(requestPostProcessor(path)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    Map<String, JSONObject> treeNodeMap = new HashMap<>();
    String body = result.getResponse().getContentAsString();
    JSONObject rootNode = null;

    JSONArray baseLayerTreeNodes = new JSONObject(body).getJSONArray("baseLayerTreeNodes");
    for (int i = 0; i < baseLayerTreeNodes.length(); i++) {
      JSONObject node = (JSONObject) baseLayerTreeNodes.get(i);
      String id = node.getString("id");
      assertFalse(treeNodeMap.containsKey(id), String.format("node %s appears multiple times", id));
      treeNodeMap.put(id, node);

      if (node.getBoolean("root")) {
        assertNull(rootNode, "Root node already exists");
        rootNode = node;
      }
    }

    assertNotNull(rootNode, "no root node found");
    assertEquals(
        1, rootNode.getJSONArray("childrenIds").length(), "root node had wrong amount of children");

    JSONObject osmNode = treeNodeMap.get(rootNode.getJSONArray("childrenIds").get(0));
    assertNotNull(osmNode, "first child of root is not valid");
    assertEquals(
        1,
        osmNode.getJSONArray("childrenIds").length(),
        "first node of root had wrong amount of children");
    assertEquals(
        1,
        treeNodeMap.get(osmNode.getJSONArray("childrenIds").get(0)).getInt("appLayerId"),
        "incorrect appLayerId for first child");
  }

  @Disabled("Authorization is not yet implemented")
  @Issue("https://b3partners.atlassian.net/browse/HTM-705")
  // TODO fix this test
  @Test
  /* this test changes database content */
  @Order(Integer.MAX_VALUE)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_send_401_when_application_login_required() throws Exception {
    applicationRepository.setAuthenticatedRequired(1L, true);
    mockMvc
        .perform(get("/app/default/map").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }
}

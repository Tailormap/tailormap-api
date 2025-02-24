/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.viewer.model.Drawing;

import java.nio.charset.Charset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.persistence.Group.ADMIN;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DrawingControllerIntegrationTest {

  private static final String UUID_REGEX = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";
  private static final String UNKNOWN_DRAWING_ID = "a73ac8ee-1d64-44be-a05e-b6426e2c1c59";

    private static final String NEW_DRAWING_JSON =
            // spotless:off
            """
{
 "name": "Drawing 1",
 "description": "Drawing 1 description",
 "domainData": {"items": 1, "domain": "test drawings"},
 "access": "private",
 "srid": 28992,
 "featureCollection": {
 "type": "FeatureCollection",
 "features": [ {
 "type": "Feature",
 "geometry": {
 "type": "Polygon",
 "coordinates": [[[132300.928,458629.588],[132302.724,458633.881],[132302.947,458634.318],[132303.327,458634.91400000005],[132303.772,458635.463],[132304.277,458635.95800000004],[132304.834,458636.393],[132305.436,458636.76200000005],[132306.076,458637.061],[132306.746,458637.28599999996],[132307.437,458637.433],[132308.141,458637.502],[132308.847,458637.49],[132309.548,458637.399],[132309.586,458637.39099999995],[132310.246,458637.205],[132311.059,458639.08],[132308.945,458639.943],[132306.112,458641.216],[132305.358,458639.943],[132304.898,458639.368],[132304.292,458638.757],[132303.703,458638.277],[132302.98,458637.805],[132302.304,458637.459],[132301.497,458637.14699999994],[132300.764,458636.94999999995],[132298.981,458636.524],[132297.813,458636.3460000001],[132296.568,458636.24199999997],[132295.387,458636.223],[132294.148,458636.288],[132292.419,458636.46499999997],[132290.614,458636.73099999997],[132288.866,458637.069],[132287.14,458637.485],[132270.926,458640.482],[132267.328,458613.3950000001],[132264.028,458607.445],[132258.431,458602.51900000003],[132259.646,458600.0],[132260.791,458597.624],[132267.141,458592.053],[132271.287,458591.25299999997],[132284.279,458588.227],[132294.24,458585.92399999994],[132295.651,458595.245],[132296.248,458600.0],[132297.991,458613.87],[132300.928,458629.588]]]
 },
 "properties": { "prop0": "value0" }
 },
 {
 "type": "Feature",
 "geometry": {
 "type": "Point",
 "coordinates": [ 132300, 458629]
 },
 "properties": { "prop0": "value1", "prop1": 0.0, "rendering": { "fill": "red", "stroke": "black" }}
 }
 ] } }
""";
  // spotless:on

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void deleteNonExistentDrawingAuthenticated() throws Exception {
    final String url = apiBasePath + "/drawing/" + UNKNOWN_DRAWING_ID;

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Drawing not found"));
  }

  @Test
  void deleteNonExistentDrawingUnAuthenticated() throws Exception {
    final String url = apiBasePath + "/drawing/" + UNKNOWN_DRAWING_ID;

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteDrawingUnAuthenticated() throws Exception {
    final String url = apiBasePath + "/drawing/a73ac8ee-1d64-44be-a05e-b6426e2c1c59";

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @Order(10)
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void createDrawing() throws Exception {
    final String url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(NEW_DRAWING_JSON))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // API created values
        .andExpect(jsonPath("$.id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        .andExpect(jsonPath("$.srid").value(28992))
        // given values
        .andExpect(jsonPath("$.name").value("Drawing 1"))
        .andExpect(jsonPath("$.access").value("private"))
        .andExpect(jsonPath("$.description").value("Drawing 1 description"))
        .andExpect(jsonPath("$.domainData").exists())
        .andExpect(jsonPath("$.domainData.items").value(1))
        .andExpect(jsonPath("$.domainData.domain").value("test drawings"))
        .andExpect(jsonPath("$.featureCollection.features[0].geometry.type")
            .value("Polygon"))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.prop0")
            .value("value0"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(20)
  void listDrawings() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].name").value("Drawing 1"))
        .andExpect(jsonPath("$[0].description").value("Drawing 1 description"));
  }

  @Test
  @Order(10)
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void updateDrawing() throws Exception {
    // this test cuts a corner by assuming that the first drawing in the list is the one we just created
    String url = apiBasePath + "/drawing/list";
    MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    assertNotNull(body, "response body should not be null");

    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    Drawing drawing = objectMapper.readValue(body, Drawing[].class)[0];

    UUID drawingId = drawing.getId();
    Integer oldVersion = drawing.getVersion();
    String oldName = drawing.getName();

    drawing.setDescription("Edited drawing 2 description");

    url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(Charset.defaultCharset())
            .content(objectMapper.writeValueAsString(drawing)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // API-created values
        .andExpect(jsonPath("$.id").value(drawingId))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        .andExpect(jsonPath("$.updatedBy").value("tm-admin"))
        .andExpect(jsonPath("$.srid").value(28992))
        .andExpect(jsonPath("$.version").value(oldVersion + 1))
        // given values
        .andExpect(jsonPath("$.access").value("private"))
        .andExpect(jsonPath("$.name").value(oldName))
        .andExpect(jsonPath("$.description").value("Edited drawing 2 description"))
        .andExpect(jsonPath("$.domainData").exists())
        .andExpect(jsonPath("$.domainData.items").value(1))
        .andExpect(jsonPath("$.domainData.domain").value("test drawings"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(30)
  void deleteDrawings() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    final MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    assertNotNull(body, "response body should not be null");

    final List<String> drawingIds = JsonPath.read(body, "$[*].id");
    String deleteUrl = apiBasePath + "/drawing/";
    for (String drawingId : drawingIds) {
      deleteUrl += drawingId;
      mockMvc.perform(delete(deleteUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
          .andExpect(status().isNoContent())
          .andDo(print());
    }
  }
}

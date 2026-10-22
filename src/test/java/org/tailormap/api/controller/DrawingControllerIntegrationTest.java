/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestJson.json;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.persistence.Group.ADMIN;
import static org.tailormap.api.persistence.helper.AdminAdditionalPropertyHelper.KEY_DRAWINGS_READ_ALL;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.security.TailormapAdditionalProperty;
import org.tailormap.api.security.TailormapUserDetails;
import org.tailormap.api.viewer.model.Drawing;
import tools.jackson.databind.json.JsonMapper;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(value = ExecutionMode.SAME_THREAD, reason = "we need specific ordering of tests")
class DrawingControllerIntegrationTest {

  private static final String UUID_REGEX = "[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}";
  private static final String UNKNOWN_DRAWING_ID = "a73ac8ee-1d64-44be-a05e-b6426e2c1c59";
  private static final String KNOWN_DRAWING_ID = "38faa008-013e-49d4-9528-8f58c94d8791";

  /** Read a new drawing (without id) with features from {@code drawings/new-drawing.json}. */
  private static Drawing newDrawing() throws IOException {
    try (InputStream in = new ClassPathResource("drawings/new-drawing.json").getInputStream()) {
      return JsonMapper.shared().readValue(in, Drawing.class);
    }
  }

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
  void get_preexisting_drawing() throws Exception {
    final String url = apiBasePath + "/app/default/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(KNOWN_DRAWING_ID))
        .andExpect(jsonPath("$.id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        .andExpect(jsonPath("$.srid").value(28992))
        .andExpect(jsonPath("$.name").value("Testcase"))
        .andExpect(jsonPath("$.description").value("A drawing that is inserted as part of the testdata"))
        .andExpect(jsonPath("$.domainData").exists())
        .andExpect(jsonPath("$.domainData.items").value(1))
        .andExpect(jsonPath("$.domainData.domain").value("test drawings"))
        .andExpect(jsonPath("$.featureCollection.features[0].geometry.type")
            .value("Polygon"))
        .andExpect(jsonPath("$.featureCollection.features[0].id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.type")
            .value("POLYGON"))
        .andExpect(jsonPath("$.featureCollection.features[1].properties.style.fillColor")
            .value("rgb(117, 117, 117)"));
  }

  @Test
  @Order(10)
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void create_drawing_by_admin() throws Exception {
    final String url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(newDrawing())))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // API created values
        .andExpect(jsonPath("$.id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        // srid from /app/default application
        .andExpect(jsonPath("$.srid").value(28992))
        // given values
        .andExpect(jsonPath("$.name").value("Drawing 1"))
        .andExpect(jsonPath("$.description").value("Drawing 1 description"))
        .andExpect(jsonPath("$.domainData").exists())
        .andExpect(jsonPath("$.domainData.items").value(1))
        .andExpect(jsonPath("$.domainData.domain").value("test drawings"))
        .andExpect(jsonPath("$.featureCollection.features[0].geometry.type")
            .value("Polygon"))
        .andExpect(jsonPath("$.featureCollection.features[0].id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.prop0")
            .value("value0"))
        .andExpect(jsonPath("$.featureCollection.features[1].properties.rendering.fill")
            .value("red"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(20)
  void list_drawings_for_admin() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[-1].name").value("Drawing 1"))
        .andExpect(jsonPath("$[-1].description").value("Drawing 1 description"));
  }

  @Test
  @WithMockUser(
      username = "user",
      authorities = {"test-bar"})
  @Order(20)
  void list_drawings_for_other_user() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // no drawings should be returned as we only added a "private" drawing
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0]").doesNotHaveJsonPath());
  }

  @Test
  @Order(20)
  void list_drawings_for_read_all_user() throws Exception {
    authenticateAsUserWithProperty(KEY_DRAWINGS_READ_ALL);
    final String url = apiBasePath + "/drawing/list";
    final MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andReturn();

    // drawings of other users should be returned
    final List<String> drawingIds = JsonPath.read(result.getResponse().getContentAsString(), "$[*].id");
    assertThat("KNOWN_DRAWING_ID should be in the list", drawingIds, hasItem(KNOWN_DRAWING_ID));
  }

  @Test
  @Order(20)
  void list_drawings_for_anonymous() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // no drawings should be returned as we only added a "private" drawing
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0]").doesNotHaveJsonPath());
  }

  @Test
  @Order(30)
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void update_drawing_by_admin() throws Exception {
    String url = apiBasePath + "/drawing/list";
    MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    assertNotNull(body, "response body should not be null");

    Drawing[] drawings = JsonMapper.shared().readValue(body, Drawing[].class);
    // Select last drawing in collection
    Drawing drawing = drawings.length == 0 ? null : drawings[drawings.length - 1];
    assertNotNull(drawing, "drawing should not be null");

    String drawingId = drawing.getId().toString();
    assertNotEquals(KNOWN_DRAWING_ID, drawingId);
    Integer oldVersion = drawing.getVersion();
    String oldName = drawing.getName();

    // update the new drawing, createdBy and createdAt are read-only and should be ignored
    String updatedDrawing = json(newDrawing()
        .id(drawing.getId())
        .description("Edited drawing 2 description")
        .createdBy("forged-user")
        .createdAt(OffsetDateTime.parse("2000-01-01T00:00:00Z"))
        .version(oldVersion));

    url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(Charset.defaultCharset())
            .content(updatedDrawing))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // API-created values
        .andExpect(jsonPath("$.id").value(drawingId))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        .andExpect(jsonPath("$.createdAt", not(startsWith("2000-01-01"))))
        .andExpect(jsonPath("$.updatedBy").value("tm-admin"))
        .andExpect(jsonPath("$.srid").value(28992))
        .andExpect(jsonPath("$.version").value(oldVersion + 1))
        // given values
        .andExpect(jsonPath("$.name").value(oldName))
        .andExpect(jsonPath("$.description").value("Edited drawing 2 description"))
        .andExpect(jsonPath("$.domainData").exists())
        .andExpect(jsonPath("$.domainData.items").value(1))
        .andExpect(jsonPath("$.domainData.domain").value("test drawings"))
        .andExpect(jsonPath("$.featureCollection.features[0].geometry.type")
            .value("Polygon"))
        .andExpect(jsonPath("$.featureCollection.features[0].id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.prop0")
            .value("value0"))
        .andExpect(jsonPath("$.featureCollection.features[1].properties.rendering.fill")
            .value("red"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(1)
  void get_nonexistent_drawing_admin() throws Exception {
    final String url = apiBasePath + "/app/default/drawing/" + UNKNOWN_DRAWING_ID;

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Drawing not found"));
  }

  @Test
  @Order(1)
  void get_nonexistent_drawing_anonymous() throws Exception {
    final String url = apiBasePath + "/app/default/drawing/" + UNKNOWN_DRAWING_ID;

    // Same response as for an existing drawing, so the existence of a drawing is not disclosed
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @Order(1)
  void get_existing_drawing_anonymous() throws Exception {
    final String url = apiBasePath + "/app/default/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @WithMockUser(
      username = "user",
      password = "user",
      authorities = {"test-bar"})
  @Order(1)
  void get_private_drawing_other_user() throws Exception {
    final String url = apiBasePath + "/app/default/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Drawing not found"));
  }

  @Test
  @Order(1)
  void get_private_drawing_read_all_user() throws Exception {
    authenticateAsUserWithProperty(KEY_DRAWINGS_READ_ALL);
    final String url = apiBasePath + "/app/default/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(KNOWN_DRAWING_ID));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(1)
  void get_private_drawing_admin() throws Exception {
    final String url = apiBasePath + "/app/default/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(KNOWN_DRAWING_ID));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(35)
  void get_drawing_in_default_application_srs() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    final MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    assertNotNull(body, "response body should not be null");

    final String drawingId = JsonPath.read(body, "$[-1].id");
    assertNotEquals(KNOWN_DRAWING_ID, drawingId);
    final String getDrawingUrl = apiBasePath + "/app/default/drawing/" + drawingId;

    mockMvc.perform(get(getDrawingUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(getDrawingUrl)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andExpect(jsonPath("$.id").value(drawingId))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        .andExpect(jsonPath("$.updatedBy").value("tm-admin"))
        .andExpect(jsonPath("$.srid").value(28992))
        // given values
        .andExpect(jsonPath("$.description").value("Edited drawing 2 description"))
        .andExpect(jsonPath("$.domainData").exists())
        .andExpect(jsonPath("$.domainData.items").value(1))
        .andExpect(jsonPath("$.domainData.domain").value("test drawings"))
        .andExpect(jsonPath("$.featureCollection.features[0].geometry.type")
            .value("Polygon"))
        .andExpect(jsonPath("$.featureCollection.features[0].id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.properties")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.featureCollection.features[0].properties.prop0")
            .value("value0"))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.prop0")
            .value("value0"))
        .andExpect(jsonPath("$.featureCollection.features[1].properties.rendering.fill")
            .value("red"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(35)
  void get_drawing_in_foreign_application_srs() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    final MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    assertNotNull(body, "response body should not be null");

    final String drawingId = JsonPath.read(body, "$[-1].id");
    assertNotEquals(KNOWN_DRAWING_ID, drawingId);
    final String getDrawingUrl = apiBasePath + "/app/austria/drawing/" + drawingId;

    mockMvc.perform(get(getDrawingUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(getDrawingUrl)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andExpect(jsonPath("$.id").value(drawingId))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        .andExpect(jsonPath("$.updatedBy").value("tm-admin"))
        .andExpect(jsonPath("$.srid").value(3857))
        // given values
        .andExpect(jsonPath("$.description").value("Edited drawing 2 description"))
        .andExpect(jsonPath("$.domainData").exists())
        .andExpect(jsonPath("$.domainData.items").value(1))
        .andExpect(jsonPath("$.domainData.domain").value("test drawings"))
        .andExpect(jsonPath("$.featureCollection.features[0].geometry.type")
            .value("Polygon"))
        .andExpect(jsonPath("$.featureCollection.features[0].id", matchesPattern(UUID_REGEX)))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.properties")
            .doesNotHaveJsonPath())
        .andExpect(jsonPath("$.featureCollection.features[0].properties.prop0")
            .value("value0"))
        .andExpect(jsonPath("$.featureCollection.features[0].properties.prop0")
            .value("value0"))
        .andExpect(jsonPath("$.featureCollection.features[1].properties.rendering.fill")
            .value("red"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(1)
  void delete_nonexistent_drawing_admin() throws Exception {
    final String url = apiBasePath + "/drawing/" + UNKNOWN_DRAWING_ID;

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Drawing not found"));
  }

  @Test
  @Order(1)
  void delete_nonexistent_drawing_anonymous() throws Exception {
    final String url = apiBasePath + "/drawing/" + UNKNOWN_DRAWING_ID;

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @Order(1)
  void delete_drawing_anonymous() throws Exception {
    final String url = apiBasePath + "/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.url").value("/login"));
  }

  @Test
  @WithMockUser(
      username = "user",
      password = "user",
      authorities = {"test-bar"})
  @Order(1)
  void delete_drawing_other_user() throws Exception {
    final String url = apiBasePath + "/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  @Order(1)
  void delete_drawing_read_all_user() throws Exception {
    authenticateAsUserWithProperty(KEY_DRAWINGS_READ_ALL);
    final String url = apiBasePath + "/drawing/" + KNOWN_DRAWING_ID;

    mockMvc.perform(delete(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(403));
  }

  @Test
  @WithMockUser(
      username = "user",
      password = "user",
      authorities = {"test-bar"})
  @Order(1)
  void update_drawing_other_user_with_forged_created_by() throws Exception {
    final String drawing = json(newDrawing()
        .id(UUID.fromString(KNOWN_DRAWING_ID))
        .createdBy("user")
        .version(1));

    final String url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(drawing))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Drawing not found"));
  }

  @Test
  @Order(1)
  void update_drawing_read_all_user_with_forged_created_by() throws Exception {
    // a user that can read but not save the drawing gets past the read check, so this verifies the save check uses
    // the stored owner and not the forged owner from the request
    authenticateAsUserWithProperty(KEY_DRAWINGS_READ_ALL);
    final String drawing = json(newDrawing()
        .id(UUID.fromString(KNOWN_DRAWING_ID))
        .createdBy("user")
        .version(1));

    final String url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(drawing))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(403));

    final String getUrl = apiBasePath + "/app/default/drawing/" + KNOWN_DRAWING_ID;
    mockMvc.perform(get(getUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(getUrl)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Testcase"))
        .andExpect(jsonPath("$.createdBy").value("tm-admin"))
        .andExpect(jsonPath("$.version").value(1));
  }

  @Test
  @WithMockUser(
      username = "user",
      password = "user",
      authorities = {"test-bar"})
  @Order(1)
  void update_nonexistent_drawing() throws Exception {
    // same response as for an existing drawing the user can not read, so the existence of a drawing is not
    // disclosed
    final String drawing =
        json(newDrawing().id(UUID.fromString(UNKNOWN_DRAWING_ID)).version(1));

    final String url = apiBasePath + "/app/default/drawing";
    mockMvc.perform(put(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .contentType(MediaType.APPLICATION_JSON)
            .content(drawing))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.message").value("Drawing not found"));
  }

  /**
   * Authenticate as a non-owner user with the given additional property set to true. The test security context is
   * cleared after each test.
   */
  private static void authenticateAsUserWithProperty(String propertyKey) {
    TailormapUserDetails principal = new TailormapUserDetails() {
      @Override
      public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("test-bar"));
      }

      @Override
      public String getPassword() {
        return "";
      }

      @Override
      public String getUsername() {
        return "user";
      }

      @Override
      public String getOrganisation() {
        return null;
      }

      @Override
      public Collection<TailormapAdditionalProperty> getAdditionalGroupProperties() {
        return List.of(new TailormapAdditionalProperty(propertyKey, false, true));
      }
    };
    TestSecurityContextHolder.setAuthentication(new UsernamePasswordAuthenticationToken(
        principal, principal.getPassword(), principal.getAuthorities()));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(31)
  void update_drawing_with_wrong_version_by_admin() throws Exception {
    String url = apiBasePath + "/drawing/list";
    MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andReturn();

    Drawing[] drawings = JsonMapper.shared().readValue(result.getResponse().getContentAsString(), Drawing[].class);
    // Select last drawing in collection
    Drawing drawing = drawings.length == 0 ? null : drawings[drawings.length - 1];
    assertNotNull(drawing, "drawing should not be null");
    assertNotEquals(KNOWN_DRAWING_ID, drawing.getId().toString());

    final String getUrl = apiBasePath + "/app/default/drawing/" + drawing.getId();
    final String putUrl = apiBasePath + "/app/default/drawing";

    // both an outdated and a higher than current version must be rejected
    for (int version : new int[] {drawing.getVersion() - 1, drawing.getVersion() + 1}) {
      String updatedDrawing = json(newDrawing()
          .id(drawing.getId())
          .description("Should not be stored")
          .version(version));

      mockMvc.perform(put(putUrl)
              .accept(MediaType.APPLICATION_JSON)
              .with(setServletPath(putUrl))
              .contentType(MediaType.APPLICATION_JSON)
              .characterEncoding(Charset.defaultCharset())
              .content(updatedDrawing))
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.code").value(409))
          .andExpect(jsonPath("$.message").value("Drawing has been updated by another user"));
    }

    mockMvc.perform(get(getUrl).accept(MediaType.APPLICATION_JSON).with(setServletPath(getUrl)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(drawing.getVersion()))
        .andExpect(jsonPath("$.description").value(drawing.getDescription()));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  @Order(Integer.MAX_VALUE)
  void delete_drawings_by_admin() throws Exception {
    final String url = apiBasePath + "/drawing/list";
    final MvcResult result = mockMvc.perform(
            get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(status().is(200))
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    assertNotNull(body, "response body should not be null");

    final List<String> drawingIds = JsonPath.read(body, "$[*].id");
    assertNotNull(drawingIds, "drawingIds should not be null");
    assertThat("KNOWN_DRAWING_ID should be in the list", drawingIds, hasItem(KNOWN_DRAWING_ID));

    drawingIds.stream()
        .filter(drawingId -> !drawingId.equals(KNOWN_DRAWING_ID))
        .forEach(drawingId -> {
          String deleteUrl = apiBasePath + "/drawing/" + drawingId;
          try {
            mockMvc.perform(delete(deleteUrl)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(setServletPath(deleteUrl)))
                .andExpect(status().isNoContent());
          } catch (Exception e) {
            fail("Deleting a drawing failed", e);
          }
        });
  }
}

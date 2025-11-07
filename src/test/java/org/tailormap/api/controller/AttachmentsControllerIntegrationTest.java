/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.controller.TestUrls.layerBegroeidTerreindeelPostgis;
import static org.tailormap.api.controller.TestUrls.layerWaterdeelOracle;
import static org.tailormap.api.controller.TestUrls.layerWegdeelSqlServer;
import static org.tailormap.api.persistence.Group.ADMIN;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Stopwatch
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttachmentsControllerIntegrationTest {

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  private static final MockMultipartFile attachmentMetadata = new MockMultipartFile(
      "attachmentMetadata",
      "metadata.json",
      "application/json",
      """
{
"attributeName":"bijlage",
"mimeType":"image/svg+xml",
"fileName":"lichtpunt.svg",
"description":"A test SVG attachment"
}
"""
          .getBytes(StandardCharsets.UTF_8));

  private static Stream<Arguments> testUrls() {
    return Stream.of(
        Arguments.of(
            "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/feature/21f95499702e3a5d05230d2ae596ea1c/attachments"),
        Arguments.of(
            "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/feature/93294fda97a19c37080849c5c1fddbf3/attachments"),
        Arguments.of(
            "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/feature/2d323d3d98a2101c01ef1c6274085254/attachments"));
  }

  private static Stream<Arguments> getFeaturesTestUrls() {
    return Stream.of(
        // docker host,table,url, feature count
        arguments(layerBegroeidTerreindeelPostgis, 132255, 458382, 10, "21f95499702e3a5d05230d2ae596ea1c"),
        arguments(layerWaterdeelOracle, 132139, 458390, 10, "93294fda97a19c37080849c5c1fddbf3"),
        arguments(layerWegdeelSqlServer, 130819, 458989, 10, "c9baca2a2f078038ace1b3a1acf8e0c8"));
  }

  private static final String layerNotEditableUrl =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:bak/feature/dbbe3dd9c3e45f1261faf5f74c67e19e/attachments";

  private static final String attachmentsNotSupportedUrl =
      "/app/default/layer/lyr:snapshot-geoserver:postgis:osm_polygon/feature/299933373/attachments";

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Order(1)
  @ParameterizedTest
  @MethodSource("testUrls")
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void addAttachment(String url) throws Exception {
    url = apiBasePath + url;

    byte[] svgBytes = new ClassPathResource("test/lichtpunt.svg").getContentAsByteArray();

    MockMultipartFile svgFile = new MockMultipartFile("attachment", "lichtpunt.svg", "image/svg+xml", svgBytes);

    mockMvc.perform(MockMvcRequestBuilders.multipart(url)
            .file(attachmentMetadata)
            .file(svgFile)
            .with(request -> {
              request.setMethod("PUT");
              return request;
            })
            .with(setServletPath(url))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.description").value("A test SVG attachment"))
        .andExpect(jsonPath("$.fileName").value("lichtpunt.svg"))
        .andExpect(jsonPath("$.mimeType").value("image/svg+xml"))
        .andExpect(jsonPath("$.attachmentId").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.createdBy").isNotEmpty())
        .andExpect(jsonPath("$.attachmentSize").value(svgBytes.length));
  }

  @Order(1)
  @ParameterizedTest
  @MethodSource("testUrls")
  void addAttachmentUnauthorised(String url) throws Exception {
    url = apiBasePath + url;

    byte[] svgBytes = new ClassPathResource("test/lichtpunt.svg").getContentAsByteArray();

    MockMultipartFile svgFile = new MockMultipartFile("attachment", "lichtpunt.svg", "image/svg+xml", svgBytes);

    mockMvc.perform(MockMvcRequestBuilders.multipart(url)
            .file(attachmentMetadata)
            .file(svgFile)
            .with(request -> {
              request.setMethod("PUT");
              return request;
            })
            .with(setServletPath(url))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Order(1)
  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void addAttachmentsNotSupported() throws Exception {
    String url = apiBasePath + attachmentsNotSupportedUrl;

    byte[] svgBytes = new ClassPathResource("test/lichtpunt.svg").getContentAsByteArray();

    MockMultipartFile svgFile = new MockMultipartFile("attachment", "lichtpunt.svg", "image/svg+xml", svgBytes);

    mockMvc.perform(MockMvcRequestBuilders.multipart(url)
            .file(attachmentMetadata)
            .file(svgFile)
            .with(request -> {
              request.setMethod("PUT");
              return request;
            })
            .with(setServletPath(url))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andDo(print())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Layer does not support attachments"));
  }

  @Order(1)
  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void addAttachmentsToNonEditableLayer() throws Exception {
    String url = apiBasePath + layerNotEditableUrl;

    byte[] svgBytes = new ClassPathResource("test/lichtpunt.svg").getContentAsByteArray();

    MockMultipartFile svgFile = new MockMultipartFile("attachment", "lichtpunt.svg", "image/svg+xml", svgBytes);

    mockMvc.perform(MockMvcRequestBuilders.multipart(url)
            .file(attachmentMetadata)
            .file(svgFile)
            .with(request -> {
              request.setMethod("PUT");
              return request;
            })
            .with(setServletPath(url))
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andDo(print())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Layer is not editable"));
  }

  @Order(2)
  @ParameterizedTest
  @MethodSource("testUrls")
  void listAttachments(String url) throws Exception {
    url = apiBasePath + url;

    mockMvc.perform(get(url).with(setServletPath(url)).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.[0].description").value("A test SVG attachment"))
        .andExpect(jsonPath("$.[0].fileName").value("lichtpunt.svg"))
        .andExpect(jsonPath("$.[0].mimeType").value("image/svg+xml"))
        .andExpect(jsonPath("$.[0].attachmentId").isNotEmpty())
        .andExpect(jsonPath("$.[0].createdAt").isNotEmpty())
        .andExpect(jsonPath("$.[0].createdBy").isNotEmpty())
        .andExpect(jsonPath("$.[0].attachmentSize").isNotEmpty());
  }

  @Order(2)
  @ParameterizedTest
  @MethodSource("testUrls")
  void getAttachment(String url) throws Exception {
    url = apiBasePath + url;

    // First get the list of attachments to retrieve the attachmentId
    String responseContent = mockMvc.perform(
            get(url).with(setServletPath(url)).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // Extract attachmentId from the response (assuming it's the first attachment)
    String attachmentId = JsonPath.read(responseContent, "$.[0].attachmentId");
    // Now get the actual attachment
    String attachmentUrl = url.substring(0, url.indexOf("/feature")) + "/attachment/" + attachmentId;

    mockMvc.perform(get(attachmentUrl)
            .with(setServletPath(attachmentUrl))
            .accept(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/svg+xml"))
        .andExpect(header().string("Content-Type", "image/svg+xml"))
        .andExpect(header().string("Content-Disposition", "inline; filename=\"lichtpunt.svg\""))
        .andExpect(content()
            .bytes(new ClassPathResource("test/lichtpunt.svg")
                .getInputStream()
                .readAllBytes()));
  }

  @Order(2)
  @ParameterizedTest
  @MethodSource("getFeaturesTestUrls")
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void getFeaturesWithAttachments(String url, int x, int y, int distance, String gmlId) throws Exception {

    String getUrl = apiBasePath + url + "/features";
    mockMvc.perform(get(getUrl)
            .accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(getUrl))
            .param("x", String.valueOf(x))
            .param("y", String.valueOf(y))
            .param("distance", String.valueOf(distance))
            .param("withAttachments", "true"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.features").isArray())
        .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
        .andExpect(jsonPath("$.features[0].attributes").isNotEmpty())
        .andExpect(jsonPath("$.attachmentMetadata[0].maxAttachmentSize").value(4_000_000))
        .andExpect(jsonPath("$.attachmentMetadata[0].mimeType").value("image/jpeg, image/svg+xml"))
        .andExpect(jsonPath("$.attachmentMetadata[0].attributeName").value("bijlage"))
        .andExpect(jsonPath("$.features[0].attachments").isArray())
        .andExpect(jsonPath("$.features[0].attachments[0].fileName").value("lichtpunt.svg"));
  }

  @Order(Integer.MAX_VALUE)
  @ParameterizedTest
  @MethodSource("testUrls")
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void deleteAttachment(String url) throws Exception {
    url = apiBasePath + url;

    // First get the list of attachments to retrieve the attachmentId
    String responseContent = mockMvc.perform(
            get(url).with(setServletPath(url)).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // Extract attachmentId from the response (assuming it's the first attachment)
    String attachmentId = JsonPath.read(responseContent, "$.[0].attachmentId");
    // Now get the actual attachment
    String attachmentUrl = url.substring(0, url.indexOf("/feature")) + "/attachment/" + attachmentId;

    mockMvc.perform(delete(attachmentUrl)
            .with(setServletPath(attachmentUrl))
            .accept(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(status().isNoContent());
  }

  @Order(2)
  @ParameterizedTest
  @MethodSource("testUrls")
  void deleteAttachmentUnauthorised(String url) throws Exception {
    url = apiBasePath + url;

    // First get the list of attachments to retrieve the attachmentId
    String responseContent = mockMvc.perform(
            get(url).with(setServletPath(url)).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // Extract attachmentId from the response (assuming it's the first attachment)
    String attachmentId = JsonPath.read(responseContent, "$.[0].attachmentId");
    // Now get the actual attachment
    String attachmentUrl = url.substring(0, url.indexOf("/feature")) + "/attachment/" + attachmentId;

    mockMvc.perform(delete(attachmentUrl)
            .with(setServletPath(attachmentUrl))
            .accept(MediaType.APPLICATION_OCTET_STREAM))
        .andExpect(status().isUnauthorized());
  }
}

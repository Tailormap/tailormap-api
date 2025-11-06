/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.persistence.Group.ADMIN;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @BeforeAll
  void initialize() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/feature/21f95499702e3a5d05230d2ae596ea1c/attachment",
        "/app/default/layer/lyr:snapshot-geoserver:oracle:WATERDEEL/feature/93294fda97a19c37080849c5c1fddbf3/attachment",
        "/app/default/layer/lyr:snapshot-geoserver:sqlserver:wegdeel/feature/2d323d3d98a2101c01ef1c6274085254/attachment"
      })
  @WithMockUser(
      username = "tm-admin",
      authorities = {ADMIN})
  void addAttachment(String url) throws Exception {
    url = apiBasePath + url;
    MockMultipartFile attachmentMetadata = new MockMultipartFile(
        "attachmentMetadata",
        "metadata.json",
        "application/json",
        """
{
"attributeName":"attachmentName",
"mimeType":"image/svg+xml",
"fileName":"lichtpunt.svg",
"description":"A test SVG attachment"
}
"""
            .getBytes(StandardCharsets.UTF_8));

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
        .andDo(print())
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
}

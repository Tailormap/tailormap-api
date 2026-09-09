/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.IntegrationTestOrdering.UPLOADS_CONTROLLER_INTEGRATION_TEST_ORDER;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadMatch;
import org.tailormap.api.repository.UploadRepository;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@PostgresIntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Stopwatch
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@Order(UPLOADS_CONTROLLER_INTEGRATION_TEST_ORDER)
class UploadsAdminControllerIntegrationTest {
  @Autowired
  private WebApplicationContext context;

  @Autowired
  private UploadRepository uploadRepository;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  private MockMvc mockMvc;

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final String UUID_PARSE_ERROR_MESSAGE =
      "JSON parse error: Cannot deserialize value of type `java.util.UUID` from String \"fail\": UUID has to be represented by standard 36-char representation";

  @BeforeAll
  void initialize() {
    // Required for Spring Data Rest APIs
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  private static String jsonStringArray(String[] array) {
    try {
      return objectMapper.writeValueAsString(array);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void validate_upload_matches() throws Exception {
    Upload water = uploadRepository.findByFilename("ISO_7001_PI_PF_007.svg").stream()
        .findAny()
        .orElseThrow(() -> new IllegalStateException(
            "Expected upload with filename 'ISO_7001_PI_PF_007.svg' not found in the database"));

    String expected = objectMapper.writeValueAsString(
        new Object[] {Map.of("id", water.getId().toString(), "hash", "cfb1b538761a21f8d39c0555ba9802b8af4d09a6")
        });

    mockMvc.perform(post(adminBasePath + "/uploads/find-by-hash/drawing-style-image")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonStringArray(new String[] {
              "cfb1b538761a21f8d39c0555ba9802b8af4d09a6", "71f8e7976e4cbc4561c9d62fb283e7f788202acb"
            })))
        .andExpect(status().is2xxSuccessful())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
        .andExpect(content().json(expected));
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void download_zipfile_of_uploads() throws Exception {
    Map<UUID, String> idsAndFilenames = uploadRepository.findAll().stream()
        .filter(upload -> upload.getFilename().endsWith(".svg"))
        .collect(Collectors.toMap(Upload::getId, Upload::getFilename));

    MvcResult download = mockMvc.perform(post(adminBasePath + "/uploads/multi")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonStringArray(idsAndFilenames.keySet().stream()
                .map(UUID::toString)
                .toArray(String[]::new))))
        .andExpect(status().is2xxSuccessful())
        .andExpect(header().string("Content-Type", "application/zip"))
        .andReturn();

    // check the zip file contains the expected files
    try (InputStream inp = new ByteArrayInputStream(download.getResponse().getContentAsByteArray());
        ZipInputStream zipInputStream = new ZipInputStream(inp, StandardCharsets.UTF_8)) {
      Set<String> fileNamesFromZip = new HashSet<>();
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String entryName = entry.getName();
        assertThat(entryName, endsWith(".svg"));
        fileNamesFromZip.add(entryName);
      }

      assertEquals(
          idsAndFilenames.size(),
          fileNamesFromZip.size(),
          "Expected number of files in the download zip does not match the uploaded files");
      assertThat(
          "Expected files in the download zip do not match the uploaded files",
          fileNamesFromZip,
          containsInAnyOrder(idsAndFilenames.values().toArray()));
    }
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void fail_download_zipfile_of_uploads() throws Exception {
    mockMvc.perform(post(adminBasePath + "/uploads/multi")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonStringArray(new String[] {"fail"})))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value(UUID_PARSE_ERROR_MESSAGE));
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void download_non_existing_upload_id_throws_404() throws Exception {
    mockMvc.perform(post(adminBasePath + "/uploads/multi")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonStringArray(new String[] {"deadbeef-1111-2222-3333-beefdeadbeef"})))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @Order(UPLOADS_CONTROLLER_INTEGRATION_TEST_ORDER)
  void delete_uploads() throws Exception {
    List<UploadMatch> uploadMatches = uploadRepository.findByHashIn(
        UploadCategory.UNRESTRICTED, List.of("b0c2a7e5059c831c289505750defcf53edac5461"));
    List<String> ids = uploadMatches.stream().map(um -> um.id().toString()).toList();
    mockMvc.perform(delete(adminBasePath + "/uploads/multi")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonStringArray(ids.toArray(new String[0]))))
        .andExpect(status().is2xxSuccessful());

    for (String id : ids) {
      assertFalse(uploadRepository.findById(UUID.fromString(id)).isPresent());
    }
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void fail_delete_invalid_id() throws Exception {
    mockMvc.perform(delete(adminBasePath + "/uploads/multi")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonStringArray(new String[] {"fail"})))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value(UUID_PARSE_ERROR_MESSAGE));
  }

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  @Order(1)
  void delete_non_existing_upload_ids_ignored() throws Exception {
    mockMvc.perform(delete(adminBasePath + "/uploads/multi")
            .contentType(MediaType.APPLICATION_JSON)
            .content(jsonStringArray(new String[] {
              "1ff99dcb-a808-499c-9af4-d46b84c14fa9", "bef56ad0-b127-4180-aff0-c34793ec0655"
            })))
        .andExpect(status().isOk());
  }
}

/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadRepository;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class UploadsControllerUnhappyIntegrationTest {
  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UploadRepository uploadRepository;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void get_does_not_exist() throws Exception {
    mockMvc.perform(get(apiBasePath + "/uploads/unrestricted/a10457df-9643-4240-b70b-bf6038ec88f5/file.txt"))
        .andExpect(status().isNotFound());
  }

  @Test
  void get_with_bad_uuid() throws Exception {
    mockMvc.perform(get(apiBasePath + "/uploads/unrestricted/not-a-uuid/file.txt"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void get_with_bad_category() throws Exception {
    mockMvc.perform(get(apiBasePath + "/uploads/bad-category/not-a-uuid/file.txt"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void get_with_disallowed_category() throws Exception {
    for (UploadCategory category : UploadCategory.values()) {
      if (category.isRestricted()) {
        mockMvc.perform(get(apiBasePath + "/uploads/" + category
                + "/00000000-0000-0000-0000-000000000000/file.txt"))
            .andExpect(status().isBadRequest());
      }
    }
  }

  @Test
  void get_latest_with_disallowed_category() throws Exception {
    for (UploadCategory category : UploadCategory.values()) {
      if (category.isRestricted()) {
        mockMvc.perform(get(apiBasePath + "/uploads/" + category + "/latest"))
            .andExpect(status().isBadRequest());
      }
    }
  }

  @Test
  void get_gemeentegebied_legend_image_old_url() throws Exception {
    Upload uploadedLegend = uploadRepository
        .findWithContentByCategoryAndFilename(UploadCategory.LEGEND, "gemeentegebied-legend.png")
        .orElseThrow(() -> new RuntimeException("Upload 'gemeentegebied-legend.png' not found"));
    // this is the old (TM 12.8.3) legend URL format, which should return a 400 Bad Request because it is no longer
    // allowed
    final String path = apiBasePath
        + "/uploads/"
        + UploadCategory.LEGEND + "/" + uploadedLegend.getId() + "/gemeentegebied-legend.png";

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void get_non_existent_latest_upload() throws Exception {
    mockMvc.perform(get(apiBasePath + "/uploads/" + UploadCategory.THEME_FAVICON + "/latest"))
        .andExpect(status().isNotFound());
  }

  @Test
  void get_non_existent_category_latest_upload() throws Exception {
    mockMvc.perform(get(apiBasePath + "/uploads/%s/latest".formatted("non-existent-category")))
        .andExpect(status().isBadRequest());
  }
}

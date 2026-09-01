/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;
import static org.tailormap.api.service.UploadsService.DESCRIPTION_HEADER_NAME;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;
import org.tailormap.api.repository.UploadRepository;

@AutoConfigureMockMvc
@PostgresIntegrationTest
class LayerAttachedUploadsControllerIntegrationTest {
  private static final DateTimeFormatter httpDateHeaderFormatter = DateTimeFormatter.ofPattern(
          "EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
      .withZone(ZoneId.of("GMT"));

  @Autowired
  private UploadRepository uploadRepository;

  @Autowired
  private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  private Upload uploadedLogo;

  @BeforeEach
  void setUp() {
    uploadedLogo = uploadRepository
        .findWithContentByCategoryAndFilename(UploadCategory.LAYER_ATTACHED_FILE, "pdok_logo.png")
        .orElseThrow(() -> new RuntimeException("Upload 'pdok_logo.png' not found"));
  }

  @Test
  void unauthenticated_get_pdok_logo_upload_secured_app() throws Exception {
    final String path = apiBasePath
        + "/app/secured/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/uploads/"
        + UploadCategory.LAYER_ATTACHED_FILE + "/" + uploadedLogo.getId() + "/" + uploadedLogo.getFilename();

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void authenticated_get_pdok_logo_upload_secured_app() throws Exception {
    final String path = apiBasePath
        + "/app/secured/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/uploads/"
        + UploadCategory.LAYER_ATTACHED_FILE + "/" + uploadedLogo.getId() + "/" + uploadedLogo.getFilename();

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().exists("Last-Modified"))
        .andExpect(header().string(DESCRIPTION_HEADER_NAME, uploadedLogo.getDescription()))
        .andExpect(content().bytes(new ClassPathResource("test/pdok_logo.png").getContentAsByteArray()));
  }

  @Test
  void get_pdok_logo_upload_default_app() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/uploads/"
        + UploadCategory.LAYER_ATTACHED_FILE + "/" + uploadedLogo.getId() + "/" + uploadedLogo.getFilename();

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().exists("Last-Modified"))
        .andExpect(header().string(DESCRIPTION_HEADER_NAME, uploadedLogo.getDescription()))
        .andExpect(content().bytes(new ClassPathResource("test/pdok_logo.png").getContentAsByteArray()));
  }

  @Test
  void get_pdok_logo_upload_default_app_using_last_modified() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/uploads/"
        + UploadCategory.LAYER_ATTACHED_FILE + "/" + uploadedLogo.getId() + "/" + uploadedLogo.getFilename();

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path))
            .header(
                "If-Modified-Since",
                httpDateHeaderFormatter.format(
                    uploadedLogo.getLastModified().toInstant())))
        .andExpect(status().isNotModified());
  }

  @Test
  void get_pdok_logo_upload_default_app_using_last_modified_in_past() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/uploads/"
        + UploadCategory.LAYER_ATTACHED_FILE + "/" + uploadedLogo.getId() + "/" + uploadedLogo.getFilename();

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path))
            .header("If-Modified-Since", "Wed, 12 Jun 2001 09:48:38 GMT"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().exists("Last-Modified"))
        .andExpect(header().string(DESCRIPTION_HEADER_NAME, uploadedLogo.getDescription()))
        .andExpect(content().bytes(new ClassPathResource("test/pdok_logo.png").getContentAsByteArray()));
  }

  @Test
  void get_unrestricted_upload_default_app() throws Exception {
    Upload upload = uploadRepository
        .findWithContentByCategoryAndFilename(UploadCategory.UNRESTRICTED, "upload0.png")
        .orElseThrow(() -> new RuntimeException("Upload 'upload0.png' not found"));

    final String path = apiBasePath
        + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/uploads/"
        + UploadCategory.UNRESTRICTED + "/" + upload.getId() + "/" + upload.getFilename();

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.IMAGE_PNG))
        .andExpect(header().exists("Last-Modified"))
        .andExpect(header().string(DESCRIPTION_HEADER_NAME, upload.getDescription()))
        .andExpect(content().bytes(new ClassPathResource("test/upload.png").getContentAsByteArray()));
  }

  @Test
  void get_unrestricted_null_upload_default_app() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/uploads/"
        + UploadCategory.UNRESTRICTED;

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isNotFound());
  }

  @Test
  void get_layer_attached_file_null_upload_default_app() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/uploads/"
        + UploadCategory.LAYER_ATTACHED_FILE;

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isNotFound());
  }

  @Test
  void get_non_existing_upload_default_app() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:snapshot-geoserver:postgis:begroeidterreindeel/uploads/"
        + UploadCategory.UNRESTRICTED + "/" + "00000000-0000-0000-0000-000000000000" + "/" + "upload.png";

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path)))
        .andExpect(status().isNotFound());
  }

  @Test
  void get_non_existing_upload_default_app_using_last_modified_in_past() throws Exception {
    final String path = apiBasePath
        + "/app/default/layer/lyr:pdok-kadaster-bestuurlijkegebieden:Gemeentegebied/uploads/"
        + UploadCategory.LAYER_ATTACHED_FILE + "/00000000-0000-0000-0000-000000000000/upload.png";

    mockMvc.perform(MockMvcRequestBuilders.get(path)
            .accept(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
            .with(setServletPath(path))
            .header("If-Modified-Since", "Wed, 12 Jun 2001 09:48:38 GMT"))
        .andExpect(status().isBadRequest());
  }
}

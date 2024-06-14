/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.annotation.PostgresIntegrationTest;
import nl.b3p.tailormap.api.persistence.Upload;
import nl.b3p.tailormap.api.repository.UploadRepository;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UploadsControllerIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Autowired private UploadRepository uploadRepository;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  private static String logoUrl;

  private static MvcResult logoResult;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void test404() throws Exception {
    mockMvc
        .perform(
            get(apiBasePath + "/uploads/something/a10457df-9643-4240-b70b-bf6038ec88f5/file.txt"))
        .andExpect(status().isNotFound());
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testBadRequest() throws Exception {
    mockMvc
        .perform(get(apiBasePath + "/uploads/something/not-a-uuid/file.txt"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Order(1)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @Transactional
  void testExists() throws Exception {
    Upload logo = uploadRepository.findByCategory(Upload.CATEGORY_APP_LOGO).get(0);

    logoUrl = apiBasePath + "/uploads/%s/%s/file.txt".formatted(logo.getCategory(), logo.getId());
    logoResult =
        mockMvc
            .perform(get(logoUrl))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/svg+xml"))
            .andExpect(header().longValue("content-length", logo.getContentLength()))
            .andReturn();
  }

  @Test
  @Order(2)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testConditionalNotModified() throws Exception {
    mockMvc
        .perform(
            get(logoUrl)
                .header("If-Modified-Since", logoResult.getResponse().getHeader("Last-Modified")))
        .andExpect(status().isNotModified());
  }

  @Test
  @Order(3)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void testConditionalModified() throws Exception {
    mockMvc
        .perform(get(logoUrl).header("If-Modified-Since", "Wed, 12 Jun 2001 09:48:38 GMT"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .bytes(new ClassPathResource("test/gradient-logo.svg").getContentAsByteArray()));
  }
}

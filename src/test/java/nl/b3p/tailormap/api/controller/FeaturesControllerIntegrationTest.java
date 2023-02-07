/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.security.SecurityConfig;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = {
      HSQLDBTestProfileJPAConfiguration.class,
      FeaturesController.class,
      SecurityConfig.class,
      AuthorizationService.class,
      AppRestControllerAdvice.class,
    })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FeaturesControllerIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired ApplicationRepository applicationRepository;

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void only_filter_not_supported() throws Exception {
    mockMvc
        .perform(get("/app/1/layer/2/features").param("filter", "naam=Utrecht"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void givenOnly_XorY_shouldError() throws Exception {
    mockMvc
        .perform(get("/app/1/layer/2/features").param("x", "3"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));

    mockMvc
        .perform(get("/app/1/layer/2/features").param("y", "3"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void given_distance_NotGreaterThanZero() throws Exception {
    mockMvc
        .perform(get("/app/1/layer/2/features?y=3&y=3").param("distance", "0"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));

    mockMvc
        .perform(get("/app/1/layer/2/features?y=3&y=3").param("distance", "-1"))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(400));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void should_error_when_calling_with_nonexistent_appId() throws Exception {
    mockMvc
        .perform(get("/app/400/layer/1/features"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  @WithMockUser(
      username = "admin",
      authorities = {"Admin"})
  void should_not_find_when_called_without_appId() throws Exception {
    mockMvc.perform(get("/app/layer/features")).andExpect(status().isNotFound());
  }

  @Test
  @Order(Integer.MAX_VALUE)
  @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
  void should_send_403_when_access_denied() throws Exception {
    mockMvc
        .perform(get("/app/1/layer/2/features").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(403))
        .andExpect(jsonPath("$.message").value("Access denied"));
  }
}

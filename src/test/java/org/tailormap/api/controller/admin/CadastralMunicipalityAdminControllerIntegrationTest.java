/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.admin.model.CadastralMunicipality;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.pdok.CadastralMunicipalityService;
import org.tailormap.api.persistence.Group;

@AutoConfigureMockMvc
@Stopwatch
@PostgresIntegrationTest
// the service mock is stubbed per test
@Execution(ExecutionMode.SAME_THREAD)
class CadastralMunicipalityAdminControllerIntegrationTest {
  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private CadastralMunicipalityService cadastralMunicipalityService;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  private String url() {
    return adminBasePath + "/pdok/cadastral-municipalities";
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void returns_the_list_for_an_admin() throws Exception {
    when(cadastralMunicipalityService.isEnabled()).thenReturn(true);
    when(cadastralMunicipalityService.getCadastralMunicipalities())
        .thenReturn(new CadastralMunicipalityService.CadastralMunicipalities(
            List.of(
                new CadastralMunicipality().code("ASD02").name("Amsterdam"),
                new CadastralMunicipality().code("ATN02").name("Aalten")),
            Instant.now()));

    mockMvc.perform(get(url()))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(header().string("Cache-Control", "max-age=3600, private"))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].code").value("ASD02"))
        .andExpect(jsonPath("$[0].name").value("Amsterdam"))
        .andExpect(jsonPath("$[1].code").value("ATN02"))
        .andExpect(jsonPath("$[1].name").value("Aalten"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void returns_503_when_the_list_could_not_be_loaded() throws Exception {
    when(cadastralMunicipalityService.isEnabled()).thenReturn(true);
    when(cadastralMunicipalityService.getCadastralMunicipalities()).thenThrow(new IOException("PDOK down"));

    mockMvc.perform(get(url()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(503))
        .andExpect(jsonPath("$.message")
            .value("The list of cadastral municipalities could not be loaded from PDOK"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void returns_404_when_disabled() throws Exception {
    when(cadastralMunicipalityService.isEnabled()).thenReturn(false);

    mockMvc.perform(get(url()))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  void redirects_to_login_when_not_logged_in() throws Exception {
    mockMvc.perform(get(url()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", Matchers.endsWith("/api/unauthorized")));
  }

  @Test
  @WithMockUser(username = "some-user")
  void returns_403_for_a_non_admin() throws Exception {
    mockMvc.perform(get(url())).andExpect(status().isForbidden());
  }
}

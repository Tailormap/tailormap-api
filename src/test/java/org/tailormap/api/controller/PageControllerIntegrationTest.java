/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.tailormap.api.annotation.PostgresIntegrationTest;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class PageControllerIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Test
  void testPageNotFound() throws Exception {
    final String url = apiBasePath + "/page/does-not-exist";
    mockMvc.perform(get(url).with(setServletPath(url))).andExpect(status().isNotFound());
  }

  @Test
  void testHomePageWithFilteredTile() throws Exception {
    final String url = apiBasePath + "/page";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.name").value("home"))
        .andExpect(jsonPath("$.tiles.length()").value(Matchers.greaterThan(3)))
        .andExpect(jsonPath("$.tiles[?(@.applicationUrl == '/app/default')]").exists())
        .andExpect(
            jsonPath(
                "$.tiles[?(@.title == 'Default app')].image",
                everyItem(startsWith("http://localhost/api/uploads/portal-image/"))))
        .andExpect(jsonPath("$.tiles[?(@.title == 'About')].pageUrl").value("/page/about"))
        .andExpect(
            jsonPath(
                    "$.tiles[?(@.applicationUrl == '/app/secured' && @.title == 'Secured app (unfiltered)')]")
                .exists())
        .andExpect(
            jsonPath("$.tiles[?(@.applicationUrl == '/app/secured' && @.title == 'Secured app')]")
                .doesNotHaveJsonPath());
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {"admin"})
  void testFilteredTileShownWhenAuthorized() throws Exception {
    final String url = apiBasePath + "/page";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.name").value("home"))
        .andExpect(
            jsonPath("$.tiles[?(@.applicationUrl == '/app/secured' && @.title == 'Secured app')]")
                .exists());
  }

  @Test
  void testMenuItems() throws Exception {
    final String url = apiBasePath + "/page/home";
    String aboutItemJsonPath = "$.menu[?(@.label == 'About')].pageUrl";
    String b3pWebsiteItemJsonPath = "$.menu[?(@.label == 'B3Partners website')]";
    mockMvc
        .perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath(aboutItemJsonPath).value("/page/about"))
        .andExpect(jsonPath(b3pWebsiteItemJsonPath).doesNotHaveJsonPath());

    mockMvc
        .perform(
            get(apiBasePath + "/page/about")
                .accept(MediaType.APPLICATION_JSON)
                .with(setServletPath(url)))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath(aboutItemJsonPath).exists())
        .andExpect(jsonPath(b3pWebsiteItemJsonPath).exists());
  }
}

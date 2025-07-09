/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.controller.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.tailormap.api.TestRequestProcessor.setServletPath;

import com.jayway.jsonpath.JsonPath;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Group;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
@Stopwatch
@Order(1)
public class UniqueValuesAdminControllerIntegrationTest {
  @Value("${tailormap-api.admin.base-path}/unique-values")
  private String adminUniqueValuesPath;

  private static final String BEGROEIDTERREINDEEL_BRONHOUDER_URL = "/39/bronhouder";
  private static final String WATERDEEL_BRONHOUDER_URL = "/95/BRONHOUDER";
  private static final String WEGDEEL_BRONHOUDER_URL = "/128/bronhouder";

  @Autowired
  private MockMvc mockMvc;

  static Stream<Arguments> databaseArgumentsProvider() {
    return Stream.of(
        arguments(
            BEGROEIDTERREINDEEL_BRONHOUDER_URL,
            new String[] {"W0636", "G0344", "L0004", "W0155", "L0001", "P0026", "L0002", "G1904", "B3P"}),
        arguments(
            WATERDEEL_BRONHOUDER_URL,
            new String[] {"W0636", "P0026", "L0002", "W0155", "G1904", "G0344", "L0004"}),
        arguments(WEGDEEL_BRONHOUDER_URL, new String[] {"P0026", "G0344", "G1904", "L0004", "L0002"}));
  }

  @ParameterizedTest(name = "#{index}: should return all unique values from database: {0}")
  @MethodSource("databaseArgumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void bronhouder_unique_values_test(String url, String... expected) throws Exception {
    url = adminUniqueValuesPath + url;
    MvcResult result = mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.filterApplied").value(false))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty())
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    List<String> values = JsonPath.read(body, "$.values");
    final Set<String> uniqueValues = new HashSet<>(values);

    assertEquals(values.size(), uniqueValues.size(), "Unique values should be unique");
    assertTrue(uniqueValues.containsAll(Set.of(expected)), "not all values are present");
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void test_hidden_attribute() throws Exception {
    final String url = adminUniqueValuesPath + "/39/ligtInLandCode";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Attribute does not exist"));
  }

  @Test
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void attribute_name_required() throws Exception {
    final String url = adminUniqueValuesPath + "/39/ ";
    mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON).with(setServletPath(url)))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.message").value("Attribute name is required"));
  }

  @ParameterizedTest(
      name = "#{index}: should return unique bronhouder from database when filtered on bronhouder: {0}")
  @MethodSource("databaseArgumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void bronhouder_with_filter_on_bronhouder_unique_values_test(String url, String... expected) throws Exception {
    url = adminUniqueValuesPath + url;
    String cqlFilter = "bronhouder='G0344'";
    if (url.contains("BRONHOUDER")) {
      // uppercase oracle cql filter
      cqlFilter = cqlFilter.toUpperCase(Locale.ROOT);
    }

    MvcResult result = mockMvc.perform(get(url).accept(MediaType.APPLICATION_JSON)
            .with(setServletPath(url))
            .param("filter", cqlFilter))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(true))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values").isNotEmpty())
        .andReturn();

    final String body = result.getResponse().getContentAsString();
    List<String> values = JsonPath.read(body, "$.values");

    assertEquals(1, values.size(), "there should only be 1 value");
    assertTrue(List.of(expected).contains(values.get(0)), "not all values are present");
  }

  @ParameterizedTest(name = "#{index}: should return no unique bronhouder from database with exclusion filter: {0}")
  @MethodSource("databaseArgumentsProvider")
  @WithMockUser(
      username = "tm-admin",
      authorities = {Group.ADMIN})
  void bronhouder_with_filter_on_inonderzoek_unique_values_test(String url) throws Exception {
    url = adminUniqueValuesPath + url;
    String cqlFilter = "inonderzoek=TRUE";
    if (url.contains("BRONHOUDER")) {
      // uppercase oracle cql filter
      cqlFilter = cqlFilter.toUpperCase(Locale.ROOT);
    }

    mockMvc.perform(get(url).with(setServletPath(url))
            .param("filter", cqlFilter)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filterApplied").value(true))
        .andExpect(jsonPath("$.values").isArray())
        .andExpect(jsonPath("$.values.length()").value(2));
  }
}

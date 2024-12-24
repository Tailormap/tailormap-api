/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.tailormap.api.annotation.PostgresIntegrationTest;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.viewer.model.Component;

@PostgresIntegrationTest
class PersistentJsonArrayPropertyIntegrationTest {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Autowired
  private WebApplicationContext context;

  @Value("${tailormap-api.admin.base-path}")
  private String adminBasePath;

  @Test
  @WithMockUser(
      username = "admin",
      authorities = {Group.ADMIN})
  void testUpdateApplicationComponents() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build(); // Required for Spring Data Rest APIs

    Application app = new Application()
        .setName("test-json-array-property")
        .setTitle("Testcase")
        .setCrs("EPSG:3857");

    MvcResult result = mockMvc.perform(post(adminBasePath + "/applications")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(app)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.components").isArray())
        .andExpect(jsonPath("$.components.length()").value(0))
        .andExpect(jsonPath("$.name").value("test-json-array-property"))
        .andReturn();

    Integer appId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

    app.setComponents(List.of(
        new Component().type("test 1"), new Component().type("test 2"), new Component().type("test 3")));

    mockMvc.perform(patch(adminBasePath + "/applications/" + appId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(getApplicationComponentsPatchBody(objectMapper, app)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.components").isArray())
        .andExpect(jsonPath("$.components.length()").value(3))
        .andExpect(jsonPath("$.components[0].type").value("test 1"))
        .andExpect(jsonPath("$.components[1].type").value("test 2"))
        .andExpect(jsonPath("$.components[2].type").value("test 3"));

    app.setComponents(List.of(
        new Component().type("test 1"),
        new Component().type("test 2 [modified without changing array size]"),
        new Component().type("test 3")));

    mockMvc.perform(patch(adminBasePath + "/applications/" + appId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(getApplicationComponentsPatchBody(objectMapper, app)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.components").isArray())
        .andExpect(jsonPath("$.components.length()").value(3))
        .andExpect(jsonPath("$.components[1].type").value("test 2 [modified without changing array size]"));

    app.setComponents(List.of(new Component().type("test 2 [shrink array]"), new Component().type("test 3")));

    mockMvc.perform(patch(adminBasePath + "/applications/" + appId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(getApplicationComponentsPatchBody(objectMapper, app)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.components").isArray())
        .andExpect(jsonPath("$.components.length()").value(2))
        .andExpect(jsonPath("$.components[0].type").value("test 2 [shrink array]"));

    app.setComponents(List.of(
        new Component().type("test 1"),
        new Component().type("test 2"),
        new Component().type("test 3"),
        new Component().type("test 4 [test growing array]")));

    mockMvc.perform(patch(adminBasePath + "/applications/" + appId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(getApplicationComponentsPatchBody(objectMapper, app)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.components").isArray())
        .andExpect(jsonPath("$.components.length()").value(4))
        .andExpect(jsonPath("$.components[3].type").value("test 4 [test growing array]"));
  }

  private static String getApplicationComponentsPatchBody(ObjectMapper objectMapper, Application app)
      throws JsonProcessingException {
    ObjectNode node = objectMapper.createObjectNode();
    node.set("components", objectMapper.convertValue(app, JsonNode.class).get("components"));
    String patchBody = objectMapper.writeValueAsString(node);
    logger.info("PATCH body: {}", patchBody);
    return patchBody;
  }
}

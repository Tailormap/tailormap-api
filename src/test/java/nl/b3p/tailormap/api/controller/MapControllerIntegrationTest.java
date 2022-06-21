/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */ package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;
import nl.b3p.tailormap.api.repository.ApplicationRepository;
import nl.b3p.tailormap.api.security.SecurityConfig;

import org.json.JSONObject;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(
        classes = {
            HSQLDBTestProfileJPAConfiguration.class,
            MapController.class,
            SecurityConfig.class
        })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MapControllerIntegrationTest {
    @Autowired ApplicationRepository applicationRepository;
    @Autowired private MockMvc mockMvc;

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    @WithMockUser(
            username = "admin",
            authorities = {"Admin"})
    void should_return_data_for_configured_app() throws Exception {
        mockMvc.perform(get("/app/1/map"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.initialExtent").isMap())
                .andExpect(jsonPath("$.maxExtent").isMap())
                .andExpect(jsonPath("$.services").isArray())
                .andExpect(jsonPath("$.appLayers").isArray())
                .andExpect(jsonPath("$.appLayers[0]").isMap())
                .andExpect(jsonPath("$.appLayers.length()").value(5))
                .andExpect(jsonPath("$.appLayers[0].hasAttributes").value(false))
                .andExpect(jsonPath("$.appLayers[1].hasAttributes").value(true))
                .andExpect(jsonPath("$.crs.code").value("EPSG:28992"))
                .andReturn();
    }

    @Test
    @WithMockUser(
            username = "admin",
            authorities = {"Admin"})
    void should_show_filtered_layer_tree() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/app/1/map"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();

        Map<String, JSONObject> treeNodeMap = new HashMap<>();
        String body = result.getResponse().getContentAsString();
        JSONObject rootNode = null;
        for (Object _node : new JSONObject(body).getJSONArray("layerTreeNodes")) {
            JSONObject node = (JSONObject) _node;
            String id = node.getString("id");
            assertFalse(
                    treeNodeMap.containsKey(id),
                    String.format("node %s appears multiple times", id));
            treeNodeMap.put(id, node);

            if (node.getBoolean("root")) {
                assertNull(rootNode, "Root node already exists");
                rootNode = node;
            }
        }

        assertNotNull(rootNode, "no root node found");
        assertEquals(
                rootNode.getJSONArray("childrenIds").length(),
                2,
                "root node had wrong amount of children");

        JSONObject groenNode = treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(0));
        assertNotNull(groenNode, "first child of root is not valid");
        assertEquals(
                groenNode.getJSONArray("childrenIds").length(),
                3,
                "first node of root had wrong amount of children");
        assertEquals(
                treeNodeMap.get(groenNode.getJSONArray("childrenIds").get(0)).getInt("appLayerId"),
                2,
                "incorrect appLayerId for first child");
        assertEquals(
                treeNodeMap.get(groenNode.getJSONArray("childrenIds").get(1)).getInt("appLayerId"),
                3,
                "incorrect appLayerId for second child");
        assertEquals(
                treeNodeMap.get(groenNode.getJSONArray("childrenIds").get(2)).getInt("appLayerId"),
                4,
                "incorrect appLayerId for third child");

        JSONObject woonplaatsenNode =
                treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(1));
        assertNotNull(woonplaatsenNode, "second child of root is not valid");
        assertEquals(
                woonplaatsenNode.getJSONArray("childrenIds").length(),
                1,
                "second node of root had wrong amount of children");
        assertEquals(
                treeNodeMap
                        .get(woonplaatsenNode.getJSONArray("childrenIds").get(0))
                        .getInt("appLayerId"),
                5,
                "incorrect appLayerId for first child (of second child)");
    }

    @Test
    @WithMockUser(
            username = "admin",
            authorities = {"NotAdmin"})
    void should_show_filtered_layer_tree_unauthorized() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/app/1/map"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();

        Map<String, JSONObject> treeNodeMap = new HashMap<>();
        String body = result.getResponse().getContentAsString();
        JSONObject rootNode = null;
        for (Object _node : new JSONObject(body).getJSONArray("layerTreeNodes")) {
            JSONObject node = (JSONObject) _node;
            String id = node.getString("id");
            assertFalse(
                    treeNodeMap.containsKey(id),
                    String.format("node %s appears multiple times", id));
            treeNodeMap.put(id, node);

            if (node.getBoolean("root")) {
                assertNull(rootNode, "Root node already exists");
                rootNode = node;
            }
        }

        assertNotNull(rootNode, "no root node found");
        assertEquals(
                rootNode.getJSONArray("childrenIds").length(),
                2,
                "root node had wrong amount of children");

        JSONObject groenNode = treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(0));
        assertNotNull(groenNode, "first child of root is not valid");
        assertEquals(
                groenNode.getJSONArray("childrenIds").length(),
                0,
                "first node of root had wrong amount of children");

        JSONObject woonplaatsenNode =
                treeNodeMap.get(rootNode.getJSONArray("childrenIds").getString(1));
        assertNotNull(woonplaatsenNode, "second child of root is not valid");
        assertEquals(
                woonplaatsenNode.getJSONArray("childrenIds").length(),
                1,
                "second node of root had wrong amount of children");
    }

    @Test
    void should_show_filtered_base_layer_tree() throws Exception {
        MvcResult result =
                mockMvc.perform(get("/app/1/map"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn();

        Map<String, JSONObject> treeNodeMap = new HashMap<>();
        String body = result.getResponse().getContentAsString();
        JSONObject rootNode = null;

        for (Object _node : new JSONObject(body).getJSONArray("baseLayerTreeNodes")) {
            JSONObject node = (JSONObject) _node;
            String id = node.getString("id");
            assertFalse(
                    treeNodeMap.containsKey(id),
                    String.format("node %s appears multiple times", id));
            treeNodeMap.put(id, node);

            if (node.getBoolean("root")) {
                assertNull(rootNode, "Root node already exists");
                rootNode = node;
            }
        }

        assertNotNull(rootNode, "no root node found");
        assertEquals(
                rootNode.getJSONArray("childrenIds").length(),
                1,
                "root node had wrong amount of children");

        JSONObject osmNode = treeNodeMap.get(rootNode.getJSONArray("childrenIds").get(0));
        assertNotNull(osmNode, "first child of root is not valid");
        assertEquals(
                osmNode.getJSONArray("childrenIds").length(),
                1,
                "first node of root had wrong amount of children");
        assertEquals(
                treeNodeMap.get(osmNode.getJSONArray("childrenIds").get(0)).getInt("appLayerId"),
                1,
                "incorrect appLayerId for first child");
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_error_when_calling_with_nonexistent_id() throws Exception {
        mockMvc.perform(get("/app/400/map"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Requested an application that does not exist"));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_not_find_when_called_without_id() throws Exception {
        mockMvc.perform(get("/app/map")).andExpect(status().isNotFound());
    }

    @Test
    /* this test changes database content */
    @Order(Integer.MAX_VALUE)
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_send_401_when_application_login_required() throws Exception {
        applicationRepository.setAuthenticatedRequired(1L, true);

        mockMvc.perform(get("/app/1/map").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.url").value("/login"));
    }
}

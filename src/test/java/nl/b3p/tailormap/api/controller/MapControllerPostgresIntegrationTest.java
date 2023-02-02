/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import nl.b3p.tailormap.api.JPAConfiguration;
import nl.b3p.tailormap.api.model.Service;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.security.SecurityConfig;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SpringBootTest(
        classes = {
            JPAConfiguration.class,
            MapController.class,
            SecurityConfig.class,
            AuthorizationService.class,
            AppRestControllerAdvice.class,
        })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("postgresql")
@Stopwatch
class MapControllerPostgresIntegrationTest {
    @Autowired private MockMvc mockMvc;

    @Test
    void services_should_be_unique() throws Exception {
        MvcResult result =
                // GET http://snapshot.tailormap.nl/api/app/1/map
                mockMvc.perform(get("/app/1/map"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.initialExtent").isMap())
                        .andExpect(jsonPath("$.maxExtent").isMap())
                        .andExpect(jsonPath("$.services").isArray())
                        .andExpect(jsonPath("$.crs.code").value("EPSG:28992"))
                        .andReturn();

        String body = result.getResponse().getContentAsString();
        List<Service> allSvc = JsonPath.read(body, "$.services");
        Set<Service> uniqueSvc = new HashSet<>(allSvc);
        assertTrue(
                1 < allSvc.size(), "there must be more than one service, otherwise change the url");
        assertEquals(
                allSvc.size(),
                uniqueSvc.size(),
                () -> ("services array contains non-unique items: " + allSvc));
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_contain_proxy_url() throws Exception {
        mockMvc.perform(get("/app/5/map"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        jsonPath(
                                        "$.services[?(@.name == 'Bestuurlijke Gebieden View Service (proxied)')].url")
                                .value(contains(nullValue())))
                .andExpect(
                        jsonPath("$.appLayers[?(@.layerName === \"Gemeentegebied\")].url")
                                // Need to use contains() because jsonPath() returns an array even
                                // when the expression resolves to a single scalar property
                                .value(contains(endsWith("/app/5/layer/15/proxy/wms"))))
                .andExpect(
                        jsonPath("$.services[?(@.name == 'PDOK HWH luchtfoto (proxied)')].url")
                                .value(contains(nullValue())))
                .andExpect(
                        jsonPath("$.appLayers[?(@.layerName === \"Actueel_ortho25\")].url")
                                .value(contains(endsWith("/app/5/layer/16/proxy/wmts"))))
                .andExpect(
                        jsonPath(
                                        "$.appLayers[?(@.layerName === \"Gemeentegebied\")].legendImageUrl")
                                .value(
                                        contains(
                                                allOf(
                                                        containsString("/app/5/layer/15/proxy/wms"),
                                                        containsString(
                                                                "request=GetLegendGraphic")))))
                .andReturn();
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_not_contain_proxied_secured_service_layer() throws Exception {
        mockMvc.perform(get("/app/5/map"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        jsonPath("$.services[?(@.name == 'Beveiligde proxy WMS')]").doesNotExist())
                .andExpect(
                        jsonPath("$.appLayers[?(@.layerName === \"Provinciegebied\")]")
                                .doesNotExist())
                .andReturn();
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_contain_description() throws Exception {
        mockMvc.perform(get("/app/1/map"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(
                        // Level description
                        jsonPath("$.layerTreeNodes[?(@.name === 'gebieden')].description")
                                .value("Enkele externe lagen van PDOK met WFS koppeling."))
                .andExpect(
                        // Application layer description
                        jsonPath(
                                        "$.layerTreeNodes[?(@.name === 'begroeidterreindeel')].description")
                                .value(
                                        contains(
                                                startsWith(
                                                        "Deze laag toont gegevens uit http://www.postgis.net/"))))
                .andReturn();
    }
}

package nl.b3p.tailormap.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.JPAConfiguration;
import nl.b3p.tailormap.api.security.AuthorizationService;
import nl.b3p.tailormap.api.security.SecurityConfig;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
@SpringBootTest(
        classes = {
            JPAConfiguration.class,
            LayerExportController.class,
            SecurityConfig.class,
            AuthorizationService.class,
            AppRestControllerAdvice.class,
        })
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("postgresql")
@Disabled
class LayerExportControllerPostgresIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void shouldReturnExportCapabilitiesWithJDBCFeatureSource() throws Exception {
        mockMvc.perform(get("/app/1/layer/9/export/capabilities"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exportable").value(Boolean.TRUE))
                .andExpect(
                        jsonPath("$.outputFormats")
                                .value(
                                        Matchers.containsInAnyOrder(
                                                "text/xml; subtype=gml/3.1.1",
                                                "DXF",
                                                "DXF-ZIP",
                                                "GML2",
                                                "KML",
                                                "SHAPE-ZIP",
                                                "application/geopackage+sqlite3",
                                                "application/gml+xml; version=3.2",
                                                "application/json",
                                                "application/vnd.google-earth.kml xml",
                                                "application/vnd.google-earth.kml+xml",
                                                "application/x-gpkg",
                                                "csv",
                                                "excel",
                                                "excel2007",
                                                "geopackage",
                                                "geopkg",
                                                "gml3",
                                                "gml32",
                                                "gpkg",
                                                "json",
                                                "text/csv",
                                                "text/xml; subtype=gml/2.1.2",
                                                "text/xml; subtype=gml/3.2")))
                .andReturn();
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void shouldReturnExportCapabilitiesWithWFSFeatureSource() throws Exception {
        mockMvc.perform(get("/app/1/layer/2/export/capabilities"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.exportable").value(Boolean.TRUE))
                .andExpect(
                        jsonPath("$.outputFormats")
                                .value(
                                        Matchers.containsInAnyOrder(
                                                "text/xml; subtype=gml/3.1.1",
                                                "application/json; subtype=geojson",
                                                "application/json",
                                                "text/xml")))
                .andReturn();
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void shouldExportGeoJSON() throws Exception {
        mockMvc.perform(
                        get(
                                "/app/1/layer/2/export/download?outputFormat=application/json&attributes=geom,naam,code,ligtInLandCode,ligtInLandNaam"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.name").value("Provinciegebied"))
                .andExpect(jsonPath("$.features.length()").value(12))
                .andExpect(jsonPath("$.features[0].geometry.type").value("MultiPolygon"))
                .andReturn();
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void shouldExportGeoPackage() throws Exception {
        mockMvc.perform(
                        get("/app/1/layer/7/export/download")
                                .param("outputFormat", "application/geopackage+sqlite3"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/geopackage+sqlite3"))
                .andReturn();
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void shouldExportGeoJSONWithFilter() throws Exception {
        mockMvc.perform(
                        get("/app/1/layer/7/export/download")
                                .param("outputFormat", "application/json")
                                .param("filter", "(BRONHOUDER IN ('G1904'))"))
                .andExpect(status().isOk())
                // https://stackoverflow.com/questions/58525387
                // .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.features[0].properties.BRONHOUDER").value("G1904"))
                .andReturn();
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void shouldExportGeoJSONWithFilterAndSort() throws Exception {
        mockMvc.perform(
                        get("/app/1/layer/7/export/download")
                                .param("outputFormat", "application/json")
                                .param("filter", "(BRONHOUDER IN ('G1904','L0002','L0004'))")
                                .param("sortBy", "CLASS")
                                .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                // .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(19))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.features[0].properties.CLASS").value("greppel, droge sloot"))
                .andReturn();

        mockMvc.perform(
                        get("/app/1/layer/7/export/download")
                                .param("outputFormat", "application/json")
                                .param("filter", "(BRONHOUDER IN ('G1904','L0002','L0004'))")
                                .param("sortBy", "CLASS")
                                .param("sortOrder", "desc"))
                .andExpect(status().isOk())
                // .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(19))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
                .andExpect(jsonPath("$.features[0].properties.CLASS").value("watervlakte"))
                .andReturn();
    }
}

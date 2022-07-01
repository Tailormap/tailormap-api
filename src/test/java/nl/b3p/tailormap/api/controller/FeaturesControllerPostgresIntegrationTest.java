/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import nl.b3p.tailormap.api.JPAConfiguration;
import nl.b3p.tailormap.api.model.Service;
import nl.b3p.tailormap.api.security.SecurityConfig;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.Stopwatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest(classes = {JPAConfiguration.class, FeaturesController.class, SecurityConfig.class})
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("postgresql")
@Stopwatch
class FeaturesControllerPostgresIntegrationTest {
    /** bestuurlijke gebieden WFS; provincies . */
    private static final String provinciesWFS = "/app/1/layer/2/features";

    /** note that for WFS 2.0.0 this is -1 and for WFS 1.0.0 this is 12! */
    private static final int provinciesWFSTotalCount = -1;

    static Stream<Arguments> argumentsProvider() {
        return Stream.of(
                // docker host,table,url, feature count
                arguments("postgis", "begroeidterreindeel", "/app/1/layer/6/features", 3662),
                arguments("oracle", "waterdeel", "/app/1/layer/7/features", 282),
                arguments("sqlserver", "wegdeel", "/app/1/layer/9/features", 5934));
    }

    private final Log logger = LogFactory.getLog(getClass());
    @Autowired private MockMvc mockMvc;

    @Value("${tailormap-api.pageSize}")
    private int pageSize;

    /**
     * requires layer "Provinciegebied" with id 2 and with wfs attributes to be configured, will
     * fail if configured postgres database is unavailable.
     *
     * @throws Exception if any
     */
    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_produce_for_valid_input_pdok_betuurlijkegebieden() throws Exception {
        mockMvc.perform(
                        get(provinciesWFS)
                                .param("x", "141247")
                                .param("y", "458118")
                                .param("simplify", "true"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
    }

    /* test EPSG:28992 (RD New/Amersfoort) */
    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_produce_for_valid_input_with_native_crs_pdok_betuurlijkegebieden()
            throws Exception {
        mockMvc.perform(
                        get(provinciesWFS)
                                .param("x", "141247")
                                .param("y", "458118")
                                .param("crs", "EPSG:28992")
                                .param("simplify", "true"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
    }

    /* test EPSG:3857 (web mercator) */
    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_produce_for_valid_input_with_alien_crs_pdok_betuurlijkegebieden() throws Exception {
        mockMvc.perform(
                        get(provinciesWFS)
                                .param("x", "577351")
                                .param("y", "6820242")
                                .param("crs", "EPSG:3857")
                                .param("simplify", "true"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
    }

    /* test EPSG:4326 (WGS84) */
    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_produce_for_valid_input_with_alien_crs_2_pdok_betuurlijkegebieden()
            throws Exception {
        mockMvc.perform(
                        get(provinciesWFS)
                                // note flipped axis
                                .param("y", "5.04173")
                                .param("x", "52.11937")
                                .param("crs", "EPSG:4326")
                                .param("simplify", "true")
                                .param("distance", /*~ 4 meter*/ "0.00004"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isNotEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
    }

    /**
     * request 2 pages data from the bestuurlijke gebieden WFS featuretype provincies.
     *
     * @throws Exception if any
     */
    @Test
    void should_return_non_empty_featurecollections_for_valid_page_from_wfs() throws Exception {
        // bestuurlijke gebieden WFS; provincies
        // page 1
        MvcResult result =
                mockMvc.perform(get(provinciesWFS).param("page", "1"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(provinciesWFSTotalCount))
                        .andExpect(jsonPath("$.page").value(1))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isNotEmpty())
                        .andExpect(jsonPath("$.features[0]").isMap())
                        .andExpect(jsonPath("$.features[0]").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                        .andExpect(jsonPath("$.features[0].attributes.naam").value("Drenthe"))
                        .andExpect(
                                jsonPath("$.features[0].attributes.ligtInLandNaam")
                                        .value("Nederland"))
                        .andExpect(jsonPath("$.columnMetadata").isArray())
                        .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
                        .andReturn();

        String body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");
        List<Service> page1Features = JsonPath.read(body, "$.features");
        assertEquals(
                pageSize,
                page1Features.size(),
                () ->
                        "there should be "
                                + pageSize
                                + " provinces in the list, but was "
                                + page1Features.size());

        // page 2
        result =
                // bestturlijke gebieden WFS; provincies
                mockMvc.perform(get(provinciesWFS).param("page", "2"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(provinciesWFSTotalCount))
                        .andExpect(jsonPath("$.page").value(2))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isNotEmpty())
                        .andExpect(jsonPath("$.columnMetadata").isArray())
                        .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
                        .andReturn();

        body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");

        List<Service> page2Features = JsonPath.read(body, "$.features");
        assertEquals(
                2,
                page2Features.size(),
                () -> "there should be 2 provinces in the list, but was " + page2Features.size());

        // check for duplicates
        int retrievedFeatsCount = page1Features.size() + page2Features.size();
        page2Features.addAll(page1Features);
        assertEquals(
                retrievedFeatsCount,
                page2Features.stream().distinct().collect(Collectors.toList()).size(),
                "there should be no duplicates in 2 sequential pages");
    }

    /**
     * request an out-of-range page of data from the bestuurlijke gebieden WFS featuretype
     * provincies.
     *
     * @throws Exception if any
     */
    @Test
    void should_return_empty_featurecollection_for_out_of_range_page_from_wfs() throws Exception {
        // bestuurlijke gebieden WFS; provincies
        // page 3
        MvcResult result =
                mockMvc.perform(get(provinciesWFS).param("page", "3"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(provinciesWFSTotalCount))
                        .andExpect(jsonPath("$.page").value(3))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isEmpty())
                        .andReturn();
        String body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");
        List<Service> features = JsonPath.read(body, "$.features");
        assertEquals(0, features.size(), "there should be 0 provinces in the list");
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_return_default_sorted_featurecollections_for_no_or_invalid_sorting_from_wfs()
            throws Exception {
        // page 1, sort by naam, no direction
        mockMvc.perform(get(provinciesWFS).param("page", "1").param("sortBy", "naam"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(provinciesWFSTotalCount))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(pageSize))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isNotEmpty())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Drenthe"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.features[9]").isMap())
                .andExpect(jsonPath("$.features[9]").isNotEmpty())
                .andExpect(jsonPath("$.features[9].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[9].geometry").isEmpty())
                .andExpect(jsonPath("$.features[9].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[9].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.columnMetadata").isArray())
                .andExpect(jsonPath("$.columnMetadata").isNotEmpty());

        // page 1, sort by naam, invalid direction
        mockMvc.perform(
                        get(provinciesWFS)
                                .param("page", "1")
                                .param("sortBy", "naam")
                                .param("sortOrder", "invalid"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(provinciesWFSTotalCount))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(pageSize))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isNotEmpty())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Drenthe"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.features[9]").isMap())
                .andExpect(jsonPath("$.features[9]").isNotEmpty())
                .andExpect(jsonPath("$.features[9].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[9].geometry").isEmpty())
                .andExpect(jsonPath("$.features[9].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[9].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.columnMetadata").isArray())
                .andExpect(jsonPath("$.columnMetadata").isNotEmpty());
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_return_sorted_featurecollections_for_valid_sorting_from_wfs() throws Exception {
        // bestuurlijke gebieden WFS; provincies
        // page 1, sort ascending by naam
        mockMvc.perform(
                        get(provinciesWFS)
                                .param("page", "1")
                                .param("sortBy", "naam")
                                .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(provinciesWFSTotalCount))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(pageSize))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isNotEmpty())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Drenthe"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.features[9]").isMap())
                .andExpect(jsonPath("$.features[9]").isNotEmpty())
                .andExpect(jsonPath("$.features[9].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[9].geometry").isEmpty())
                .andExpect(jsonPath("$.features[9].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[9].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.columnMetadata").isArray())
                .andExpect(jsonPath("$.columnMetadata").isNotEmpty());

        // page 1, sort descending by naam
        mockMvc.perform(
                        get(provinciesWFS)
                                .param("page", "1")
                                .param("sortBy", "naam")
                                .param("sortOrder", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(provinciesWFSTotalCount))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(pageSize))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isNotEmpty())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Zuid-Holland"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.features[8]").isMap())
                .andExpect(jsonPath("$.features[8]").isNotEmpty())
                .andExpect(jsonPath("$.features[8].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[8].geometry").isEmpty())
                .andExpect(jsonPath("$.features[8].attributes.naam").value("gELDERLAND"))
                .andExpect(jsonPath("$.features[8].attributes.ligtInLandNaam").value("Nederland"))
                .andExpect(jsonPath("$.columnMetadata").isArray())
                .andExpect(jsonPath("$.columnMetadata").isNotEmpty());
    }

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_return_sorted_featurecollections_for_valid_sorting_from_database()
            throws Exception {
        // begroeidterreindeel from postgis
        // page 1, sort ascending by gmlid
        mockMvc.perform(
                        get("/app/1/layer/6/features")
                                .param("page", "1")
                                .param("sortBy", "gmlid")
                                .param("sortOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(3662))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(pageSize))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isNotEmpty())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(
                        jsonPath("$.features[0].__fid")
                                .value("begroeidterreindeel.000f22d5ea3eace21bd39111a7212bd9"));

        // page 1, sort descending by gmlid
        mockMvc.perform(
                        get("/app/1/layer/6/features")
                                .param("page", "1")
                                .param("sortBy", "gmlid")
                                .param("sortOrder", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(3662))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(pageSize))
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features").isNotEmpty())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(
                        jsonPath("$.features[0].__fid")
                                .value("begroeidterreindeel.fff17bee0b9f3c51db387a0ecd364457"));
    }
    /**
     * request 2 pages of data from a database featuretype.
     *
     * @throws Exception if any
     */
    @ParameterizedTest(
            name =
                    "should return non empty featurecollections for valid page from database #{index}: database: {0}, featuretype: {1}")
    @MethodSource("argumentsProvider")
    void should_return_non_empty_featurecollections_for_valid_pages_from_database(
            String database, String tableName, String applayerUrl, int totalCcount)
            throws Exception {
        // page 1
        MvcResult result =
                mockMvc.perform(get(applayerUrl).param("page", "1"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(totalCcount))
                        .andExpect(jsonPath("$.page").value(1))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isNotEmpty())
                        .andExpect(jsonPath("$.features[0]").isMap())
                        .andExpect(jsonPath("$.features[0]").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                        .andExpect(jsonPath("$.columnMetadata").isArray())
                        .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
                        .andReturn();

        String body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");
        List<Service> page1Features = JsonPath.read(body, "$.features");
        assertEquals(
                pageSize,
                page1Features.size(),
                () ->
                        "there should be "
                                + pageSize
                                + " features in the list, but was "
                                + page1Features.size());

        // page 2
        result =
                mockMvc.perform(get(applayerUrl).param("page", "2"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(totalCcount))
                        .andExpect(jsonPath("$.page").value(2))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isNotEmpty())
                        .andExpect(jsonPath("$.features[0]").isMap())
                        .andExpect(jsonPath("$.features[0]").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                        .andExpect(jsonPath("$.columnMetadata").isArray())
                        .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
                        .andReturn();

        body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");
        List<Service> page2Features = JsonPath.read(body, "$.features");
        assertEquals(
                pageSize,
                page2Features.size(),
                () ->
                        "there should be "
                                + pageSize
                                + " features in the list, but was "
                                + page2Features.size());

        // check for duplicates
        page2Features.addAll(page1Features);
        assertEquals(
                2 * pageSize,
                page2Features.stream().distinct().collect(Collectors.toList()).size(),
                "there should be no duplicates in 2 sequential pages");
    }

    /**
     * request the same page of data from a database featuretype twice and compare.
     *
     * @throws Exception if any
     */
    @ParameterizedTest(
            name =
                    "should return same featurecollection for same page from database #{index}: database: {0}, featuretype: {1}")
    @MethodSource("argumentsProvider")
    void should_return_same_featurecollection_for_same_page_database(
            String database, String tableName, String applayerUrl, int totalCcount)
            throws Exception {
        // page 1
        MvcResult result =
                mockMvc.perform(get(applayerUrl).param("page", "1"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(totalCcount))
                        .andExpect(jsonPath("$.page").value(1))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isNotEmpty())
                        .andExpect(jsonPath("$.features[0]").isMap())
                        .andExpect(jsonPath("$.features[0]").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                        .andExpect(jsonPath("$.columnMetadata").isArray())
                        .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
                        .andReturn();

        String body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");
        List<Service> page1Features = JsonPath.read(body, "$.features");

        // page 1 again
        result =
                mockMvc.perform(get(applayerUrl).param("page", "1"))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(totalCcount))
                        .andExpect(jsonPath("$.page").value(1))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isNotEmpty())
                        .andExpect(jsonPath("$.features[0]").isMap())
                        .andExpect(jsonPath("$.features[0]").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                        .andExpect(jsonPath("$.features[0].geometry").isEmpty())
                        .andExpect(jsonPath("$.columnMetadata").isArray())
                        .andExpect(jsonPath("$.columnMetadata").isNotEmpty())
                        .andReturn();

        body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");
        List<Service> page2Features = JsonPath.read(body, "$.features");

        assertEquals(
                page1Features,
                page2Features,
                "2 identical page requests should give two identical lists of features");
    }

    /**
     * request an out-of-range page of data from a database featuretype.
     *
     * @throws Exception if any
     */
    @ParameterizedTest(
            name =
                    "should return empty featurecollection for out of range page from database #{index}: database: {0}, featuretype: {1}")
    @MethodSource("argumentsProvider")
    void should_return_empty_featurecollection_for_out_of_range_page_database(
            String database, String tableName, String applayerUrl, int totalCcount)
            throws Exception {

        // request page ...
        int page = (totalCcount / pageSize) + 5;
        MvcResult result =
                mockMvc.perform(get(applayerUrl).param("page", "" + page))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                        .andExpect(jsonPath("$.total").value(totalCcount))
                        .andExpect(jsonPath("$.page").value(page))
                        .andExpect(jsonPath("$.pageSize").value(pageSize))
                        .andExpect(jsonPath("$.features").isArray())
                        .andExpect(jsonPath("$.features").isEmpty())
                        .andReturn();
        String body = result.getResponse().getContentAsString();
        logger.trace(body);
        assertNotNull(body, "response body should not be null");
        List<Service> features = JsonPath.read(body, "$.features");
        assertEquals(0, features.size(), "there should be 0 features in the list");
    }
}

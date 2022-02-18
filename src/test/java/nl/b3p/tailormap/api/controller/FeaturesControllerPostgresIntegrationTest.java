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

import nl.b3p.tailormap.api.JPAConfiguration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {JPAConfiguration.class, FeaturesController.class})
@AutoConfigureMockMvc
@EnableAutoConfiguration
@ActiveProfiles("default")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:postgresql://127.0.0.1/tailormap",
            "spring.datasource.username=tailormap",
            "spring.datasource.password=tailormap"
        })
class FeaturesControllerPostgresIntegrationTest {
    @Autowired private MockMvc mockMvc;

    /**
     * requires layer "Provinciegebied" with id 2 and with wfs attributes to be configured, will
     * fail if configured postgres database is unavailable. TODO postgres database setup
     *
     * @throws Exception if any
     */
    @Test
    @Disabled("needs postgres database setup")
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void should_produce_for_valid_input_pdok_betuurlijkegebieden() throws Exception {
        // calling: http://localhost:8080/api/features/1/2?x=141247&y=458118
        // produces:
        //  {
        //    "features": [{
        //        "__fid": "Provinciegebied.19ce551e-bc01-46e9-b953-929318dcdf87",
        //        "attributes": {
        //                "code": "26",
        //                "identificatie": "PV26",
        //                "ligtInLandCode": "6030",
        //                "ligtInLandNaam": "Nederland",
        //                "naam": "Utrecht"
        //        }
        //      }],
        //    "columnmetadata": [
        //      {
        //        "key": "identificatie",
        //              "alias": null,
        //              "type": "string"
        //      },
        //      {
        //        "key": "naam",
        //              "alias": null,
        //              "type": "string"
        //      },
        //      {
        //        "key": "code",
        //              "alias": null,
        //              "type": "string"
        //      },
        //      {
        //        "key": "ligtInLandCode",
        //              "alias": null,
        //              "type": "string"
        //      },
        //      {
        //        "key": "ligtInLandNaam",
        //              "alias": "ligt in",
        //              "type": "string"
        //      }
        //    ]}
        mockMvc.perform(get("/1/features/2").param("x", "141247").param("y", "458118"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features").isArray())
                .andExpect(jsonPath("$.features[0]").isMap())
                .andExpect(jsonPath("$.features[0]").isNotEmpty())
                .andExpect(jsonPath("$.features[0].__fid").isNotEmpty())
                .andExpect(jsonPath("$.features[0].attributes.naam").value("Utrecht"))
                .andExpect(jsonPath("$.features[0].attributes.ligtInLandNaam").value("Nederland"));
    }
}

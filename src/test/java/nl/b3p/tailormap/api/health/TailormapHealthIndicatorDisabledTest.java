/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class})
@AutoConfigureMockMvc
@TestPropertySource(properties = "management.health.tailormap.enabled=false")
class TailormapHealthIndicatorDisabledTest {
    @Autowired private MockMvc mockMvc;

    @Test
    @SuppressWarnings("PMD.JUnitTestsShouldIncludeAssert")
    void givenADisabledIndicator_whenSendingRequest_thenReturns404() throws Exception {

        mockMvc.perform(get("/api/actuator/health/tailormap")).andExpect(status().isNotFound());
    }
}

/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
class VersionControllerIntegrationTest {
    private static String projectVersion;
    @Autowired VersionController versionController;

    @BeforeAll
    static void getVersionFromPom() {
        // set through maven surefire/failsafe plugin from pom
        projectVersion = System.getProperty("project.version");
        assumeFalse(
                null == projectVersion,
                "Project version unknown, should be set in system environment");
    }

    @Test
    void testGetVersion() throws IOException {
        assertNotNull(versionController, "versionController can not be `null` if SpringBoot works");
        assertNotNull(versionController.getVersion(), "Version info not found");

        assertEquals(
                "{\"version\":\"0.1-SNAPSHOT\", \"databaseversion\":\"46\", \"api_version\":\"v1\"}",
                versionController.getVersion(),
                "Unexpected json response.");
    }
}

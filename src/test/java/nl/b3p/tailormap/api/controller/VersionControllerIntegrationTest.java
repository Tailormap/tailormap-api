/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

/** Testcases for {@link VersionController}. */
@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class, VersionController.class})
@ActiveProfiles("test")
class VersionControllerIntegrationTest {
    private static String projectVersion;
    private static String apiVersion;
    private static String databaseVersion;
    @Autowired private VersionController versionController;

    @BeforeAll
    static void getVersionFromPom() {
        // set through maven surefire/failsafe plugin from pom
        projectVersion = System.getProperty("project.version");
        assumeFalse(
                null == projectVersion,
                "Project version unknown, should be set in system environment");
        apiVersion = System.getProperty("api.version");
        assumeFalse(null == apiVersion, "API version unknown, should be set in system environment");
        databaseVersion = System.getProperty("database.version");
        assumeFalse(
                null == databaseVersion,
                "Database version unknown, should be set in system environment");
    }

    @Test
    void testGetVersion() {
        assertNotNull(
                versionController, "versionController can not be `null` if Spring Boot works");
        assertNotNull(versionController.getVersion(), "Version info not found");

        Map<String, String> expected =
                Map.of(
                        "version",
                        projectVersion,
                        "databaseversion",
                        databaseVersion,
                        "apiVersion",
                        apiVersion);

        assertEquals(expected, versionController.getVersion(), "Unexpected version response.");
    }
}

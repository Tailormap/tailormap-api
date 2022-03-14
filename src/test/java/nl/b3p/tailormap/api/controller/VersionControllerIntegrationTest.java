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
import nl.b3p.tailormap.api.repository.MetadataRepository;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

/** Testcases for {@link VersionController}. */
@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class, VersionController.class})
@ActiveProfiles("test")
class VersionControllerIntegrationTest {
    @Autowired private VersionController versionController;
    @Autowired private MetadataRepository metadataRepository;

    @Test
    void testGetVersion() {
        assertNotNull(
                versionController, "versionController can not be `null` if Spring Boot works");
        assertNotNull(versionController.getVersion(), "Version info not found");

        String projectVersion = System.getProperty("project.version");
        assumeFalse(
                null == projectVersion,
                "Project version unknown, should be set as system property");
        String databaseVersion = System.getenv("DATABASE_VERSION");
        assumeFalse(
                null == databaseVersion,
                "Database version unknown, should be set in system environment");

        String apiVersion = System.getenv("API_VERSION");
        assumeFalse(null == apiVersion, "API version unknown, should be set in system environment");

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

    @Test
    /* this test changes database content */
    @Order(Integer.MAX_VALUE)
    void unknown_database_version() throws Exception {
        metadataRepository.deleteMetadataByConfigKey(Metadata.DATABASE_VERSION_KEY);

        String projectVersion = System.getProperty("project.version");
        assumeFalse(
                null == projectVersion,
                "Project version unknown, should be set as system property");

        String apiVersion = System.getenv("API_VERSION");
        assumeFalse(null == apiVersion, "API version unknown, should be set in system environment");

        assertNotNull(versionController.getVersion(), "Version info not found");
        Map<String, String> expected =
                Map.of(
                        "version",
                        projectVersion,
                        "databaseversion",
                        "unknown",
                        "apiVersion",
                        apiVersion);

        assertEquals(expected, versionController.getVersion(), "Unexpected version response.");
    }
}

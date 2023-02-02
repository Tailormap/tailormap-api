/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import nl.b3p.tailormap.api.repository.MetadataRepository;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Testcases for {@link VersionController}. */
@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class, VersionController.class})
@ActiveProfiles("test")
@Disabled
class VersionControllerIntegrationTest {
    @Autowired private VersionController versionController;
    @Autowired private MetadataRepository metadataRepository;

    private String databaseVersion;
    private String apiVersion;
    private String commitSha;
    private long buildDate;
    private final int buildDateDelta = 30;
    private String projectVersion;

    @BeforeEach
    void readEnvironment() {
        projectVersion = System.getProperty("project.version");
        assumeFalse(
                null == projectVersion,
                "Project version unknown, should be set as system property");
        databaseVersion = System.getenv("DATABASE_VERSION");
        assumeFalse(
                null == databaseVersion,
                "Database version unknown, should be set in system environment");

        apiVersion = System.getenv("API_VERSION");
        assumeFalse(null == apiVersion, "API version unknown, should be set in system environment");

        commitSha = System.getenv("GIT_COMMIT_ID");
        assumeFalse(null == commitSha, "Git commit unknown, should be set in system environment");

        assumeFalse(
                null == System.getenv("BUILD_DATE"),
                "Build date unknown, should be set in system environment");

        buildDate =
                LocalDateTime.parse(
                                System.getenv("BUILD_DATE"), DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .toEpochSecond(ZoneOffset.UTC);
    }

    @Test
    void testGetVersion() {
        assertNotNull(
                versionController, "versionController can not be `null` if Spring Boot works");
        assertNotNull(versionController.getVersion(), "Version info not found");

        Map<String, Object> expected =
                Map.of(
                        "version",
                        projectVersion,
                        "databaseversion",
                        databaseVersion,
                        "apiVersion",
                        apiVersion,
                        "commitSha",
                        commitSha,
                        "buildDate",
                        buildDate);

        assertEquals(
                expected.keySet(),
                versionController.getVersion().keySet(),
                "Unexpected version response, some keys are missing.");

        // buildDate can be a few seconds off when running integration tests
        assertEquals(
                (double) buildDate,
                (double)
                        LocalDateTime.parse(
                                        versionController.getVersion().get("buildDate"),
                                        DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                .toEpochSecond(ZoneOffset.UTC),
                buildDateDelta,
                "buildDate should almost be the same - there might be a temporal abberation");

        expected.entrySet().stream()
                .filter(e -> !e.getKey().equals("buildDate"))
                .forEach(
                        e -> {
                            assertEquals(
                                    e.getValue(),
                                    versionController.getVersion().get(e.getKey()),
                                    () -> "Unexpected response for: " + e.getKey());
                        });
    }

    @Test
    /* this test changes database content */
    @Order(Integer.MAX_VALUE)
    void unknown_database_version() {
        metadataRepository.deleteMetadataByConfigKey(Metadata.DATABASE_VERSION_KEY);

        assertNotNull(versionController.getVersion(), "Version info not found");
        Map<String, Object> expected =
                Map.of(
                        "version",
                        projectVersion,
                        "databaseversion",
                        "unknown",
                        "apiVersion",
                        apiVersion,
                        "commitSha",
                        commitSha,
                        "buildDate",
                        buildDate);

        assertEquals(
                expected.keySet(),
                versionController.getVersion().keySet(),
                "Unexpected version response, some keys are missing.");

        assertEquals(
                (double) buildDate,
                (double)
                        LocalDateTime.parse(
                                        versionController.getVersion().get("buildDate"),
                                        DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                .toEpochSecond(ZoneOffset.UTC),
                buildDateDelta,
                "buildDate should almost be the same - there might be a temporal abberation");

        expected.entrySet().stream()
                .filter(e -> !e.getKey().equals("buildDate"))
                .forEach(
                        e -> {
                            assertEquals(
                                    e.getValue(),
                                    versionController.getVersion().get(e.getKey()),
                                    () -> "Unexpected response for: " + e.getKey());
                        });
    }
}

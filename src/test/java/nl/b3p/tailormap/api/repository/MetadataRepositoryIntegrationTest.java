/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import nl.b3p.tailormap.api.HSQLDBTestProfileJPAConfiguration;
import nl.tailormap.viewer.config.metadata.Metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/** Testcases for {@link MetadataRepository}. */
@ActiveProfiles("test")
@SpringBootTest(classes = {HSQLDBTestProfileJPAConfiguration.class, Metadata.class})
@ExtendWith(SpringExtension.class)
class MetadataRepositoryIntegrationTest {

    @Autowired private MetadataRepository metadataRepository;

    @Test
    void it_should_findByConfigKeyDefaultApplication() {
        final Metadata m = metadataRepository.findByConfigKey(Metadata.DEFAULT_APPLICATION);
        assertNotNull(m, "we should have found something");
        assertEquals("1", m.getConfigValue(), "default application is not 1");
    }

    @Test
    void it_should_findByConfigKeyDatabaseVersion() {
        final Metadata m = metadataRepository.findByConfigKey(Metadata.DATABASE_VERSION_KEY);
        assertNotNull(m, "we should have found something");
        assertEquals("46", m.getConfigValue(), "version is not 46");
    }
}

/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.tailormap.viewer.config.metadata.Metadata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy to use methods to access {@link Metadata}.
 *
 * @since 0.1
 */
public interface MetadataRepository extends JpaRepository<Metadata, Long> {

    /**
     * Find a metadata value using a known key such as {@link Metadata#DATABASE_VERSION_KEY} or
     * {@link Metadata#DEFAULT_APPLICATION}.
     *
     * @param configKey known key
     * @return the found value or {@code null}
     */
    Metadata findByConfigKey(String configKey);

    /**
     * Delete a value from the metadata using a known key such as {@link
     * Metadata#DEFAULT_APPLICATION}.
     *
     * @param configKey known key
     */
    @Transactional
    void deleteMetadataByConfigKey(String configKey);
}

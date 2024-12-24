/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.TMFeatureType;

@RepositoryRestResource(
    path = "feature-types",
    collectionResourceRel = "feature-types",
    itemResourceRel = "feature-type")
public interface FeatureTypeRepository extends JpaRepository<TMFeatureType, Long> {

  /**
   * Get a feature type by name and feature source. This is a non-deterministic operation since the combination of
   * name and feature source is not unique. Useful for testing.
   *
   * @param name The name of the feature type
   * @param featureSource The feature source of the feature type
   * @return The feature type
   */
  @PreAuthorize("permitAll()")
  Optional<TMFeatureType> getTMFeatureTypeByNameAndFeatureSource(String name, TMFeatureSource featureSource);
}

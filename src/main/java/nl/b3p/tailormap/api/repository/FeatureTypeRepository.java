/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.TMFeatureType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(
    path = "feature-types",
    collectionResourceRel = "feature-types",
    itemResourceRel = "feature-type")
public interface FeatureTypeRepository extends JpaRepository<TMFeatureType, String> {}

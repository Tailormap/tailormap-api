/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.tailormap.api.persistence.TMFeatureType;

@RepositoryRestResource(
    path = "feature-types",
    collectionResourceRel = "feature-types",
    itemResourceRel = "feature-type")
public interface FeatureTypeRepository extends JpaRepository<TMFeatureType, Long> {}

/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.tailormap.api.persistence.OIDCConfiguration;

@RepositoryRestResource(
    path = "oidc-configurations",
    collectionResourceRel = "oidc-configurations",
    itemResourceRel = "oidc-configuration")
public interface OIDCConfigurationRepository extends JpaRepository<OIDCConfiguration, Long> {}

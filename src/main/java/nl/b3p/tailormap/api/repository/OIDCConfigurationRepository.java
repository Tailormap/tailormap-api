/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.OIDCConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(
    path = "oidc-configurations",
    collectionResourceRel = "oidc-configurations",
    itemResourceRel = "oidc-configuration")
public interface OIDCConfigurationRepository extends JpaRepository<OIDCConfiguration, Long> {}

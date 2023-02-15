/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Configuration;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorizeAdmin
@RepositoryRestResource(
    path = "configs",
    collectionResourceRel = "configs",
    itemResourceRel = "config")
public interface ConfigurationRepository extends JpaRepository<Configuration, String> {
  @PreAuthorize("permitAll()")
  Optional<Configuration> findByKey(String key);

  void deleteConfigurationByKey(String configKey);
}

/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.Configuration;
import org.tailormap.api.security.annotation.PreAuthorizeAdmin;

@PreAuthorizeAdmin
@RepositoryRestResource(
    path = "configs",
    collectionResourceRel = "configs",
    itemResourceRel = "config")
public interface ConfigurationRepository extends JpaRepository<Configuration, String> {
  @PreAuthorize("permitAll()")
  Optional<Configuration> findByKey(String key);

  @PreAuthorize("permitAll()")
  default String get(String key) {
    return get(key, null);
  }

  @PreAuthorize("permitAll()")
  default String get(String key, String defaultValue) {
    return findByKey(key).map(Configuration::getValue).orElse(defaultValue);
  }

  @PreAuthorize("permitAll()")
  @Query("from Configuration c where c.key = :key and c.availableForViewer = true")
  Optional<Configuration> getAvailableForViewer(@Param("key") String key);

  void deleteConfigurationByKey(String configKey);
}

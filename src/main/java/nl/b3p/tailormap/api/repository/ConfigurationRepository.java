/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.transaction.annotation.Transactional;

@RepositoryRestResource(path = "configs", collectionResourceRel = "configs", itemResourceRel = "config")
public interface ConfigurationRepository extends JpaRepository<Configuration, String> {

  Optional<Configuration> findByKey(String key);

  @Transactional
  void deleteConfigurationByKey(String configKey);
}

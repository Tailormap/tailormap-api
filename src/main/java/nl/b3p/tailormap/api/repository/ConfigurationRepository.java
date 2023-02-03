/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {

  Configuration findByKey(String key);

  @Transactional
  void deleteConfigurationByKey(String configKey);
}

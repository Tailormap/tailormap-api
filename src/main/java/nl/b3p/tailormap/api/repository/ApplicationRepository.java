/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.List;
import nl.tailormap.viewer.config.app.Application;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Easy to use methods to access {@link Application}.
 *
 * @since 0.1
 */
public interface ApplicationRepository extends JpaRepository<Application, Long> {
  List<Application> findByName(String name);

  Application findByNameAndVersion(String name, String version);

  /**
   * Equivalent to findById, but preloads some of the necessary entities to limit the amount of
   * roundtrips.
   *
   * @param id the ID of the Application to return
   * @return the Application entity, with extra cached attributes.
   */
  @EntityGraph(
      attributePaths = {
        "startLayers.applicationLayer.service.details",
        "startLayers.applicationLayer.service.readers",
        "startLayers.applicationLayer.readers"
      })
  Application findWithGeoservicesById(Long id);

  @Transactional
  @Modifying
  @Query(value = "update Application a set a.authenticatedRequired = :required  where a.id = :id")
  int setAuthenticatedRequired(Long id, Boolean required);
}

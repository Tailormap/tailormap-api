/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.List;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorizeAdmin
@RepositoryRestResource(
    path = "geo-services",
    collectionResourceRel = "geo-services",
    itemResourceRel = "geo-service")
public interface GeoServiceRepository extends JpaRepository<GeoService, String> {
  @Override
  @NonNull
  @PreAuthorize("permitAll()")
  Optional<GeoService> findById(@NonNull String id);

  /**
   * Find multiple geo-services. Example URL:
   * /api/admin/geo-services/search/findByIds?ids=openbasiskaart&amp;ids=at-basemap
   *
   * @param ids The ids to search for
   * @return The geo services matching the ids
   */
  @NonNull
  @PreAuthorize("permitAll()")
  @Query("from GeoService s where id in :ids")
  List<GeoService> findByIds(@Param("ids") List<String> ids);

  /**
   * Find multiple geo-services except some. Example URL:
   * /api/admin/geo-services/search/getAllExcludingIds?ids=openbasiskaart&amp;ids=at-basemap
   *
   * <p>No geo services are returned if ids is an empty list.
   *
   * @param ids The ids not to include
   * @return All geo services except those matching the ids
   */
  @NonNull
  @PreAuthorize("permitAll()")
  @Query("from GeoService s where id not in :ids")
  List<GeoService> getAllExcludingIds(@Param("ids") List<String> ids);
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.security.annotation.PreAuthorizeAdmin;

@PreAuthorizeAdmin
@RepositoryRestResource(
    path = "feature-sources",
    collectionResourceRel = "feature-sources",
    itemResourceRel = "feature-source")
public interface FeatureSourceRepository extends JpaRepository<TMFeatureSource, Long> {
  TMFeatureSource findByUrl(String url);

  @Override
  @PreAuthorize(value = "permitAll()")
  @NonNull Optional<TMFeatureSource> findById(@NonNull Long id);

  @PreAuthorize(value = "permitAll()")
  List<TMFeatureSource> findByLinkedServiceId(String id);

  @NonNull @PreAuthorize("permitAll()")
  @Query("from TMFeatureSource fs where id in :ids")
  List<TMFeatureSource> findByIds(@Param("ids") List<Long> ids);

  /**
   * Find multiple feature-sources except some. Example URL:
   * /api/admin/feature-sources/search/getAllExcludingIds?ids=1,2,3
   *
   * <p>No feature sources are returned if ids is an empty list.
   *
   * @param ids The ids not to include
   * @return All feature sources except those matching the ids
   */
  @NonNull @PreAuthorize("permitAll()")
  @Query("from TMFeatureSource fs where id not in :ids")
  List<TMFeatureSource> getAllExcludingIds(@Param("ids") List<Long> ids);

  /**
   * Find a feature-source by title. This is a non-deterministic operation since the title is not unique. Useful for
   * testing.
   *
   * @param title The title of the feature-source
   * @return The feature-source
   */
  @PreAuthorize(value = "permitAll()")
  Optional<TMFeatureSource> getByTitle(String title);
}

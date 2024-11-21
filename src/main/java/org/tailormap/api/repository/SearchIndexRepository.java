/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.lang.NonNull;
import org.tailormap.api.persistence.SearchIndex;

@RepositoryRestResource(
    path = "search-indexes",
    collectionResourceRel = "search-indexes",
    itemResourceRel = "search-index")
public interface SearchIndexRepository extends JpaRepository<SearchIndex, Long> {
  List<SearchIndex> findByFeatureTypeId(Long featureTypeId);

  Optional<SearchIndex> findByName(String name);

  @NonNull
  @Query(
      value =
          "select * from search_index si, lateral jsonb_path_query(si.schedule, ('$.uuid ? (@ == \"'||:uuidToFind||'\")')::jsonpath)",
      nativeQuery = true)
  List<SearchIndex> findByTaskScheduleUuid(@Param("uuidToFind") @NonNull UUID uuid);

  List<SearchIndex> findSearchIndexById(Long id);
}

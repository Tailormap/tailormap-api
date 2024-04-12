/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.List;
import nl.b3p.tailormap.api.persistence.SearchIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(
    path = "search-indexes",
    collectionResourceRel = "search-indexes",
    itemResourceRel = "search-index")
public interface SearchIndexRepository extends JpaRepository<SearchIndex, Long> {
  List<SearchIndex> findByFeatureTypeId(Long featureTypeId);
}

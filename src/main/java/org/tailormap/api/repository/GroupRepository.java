/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.security.TailormapAdditionalProperty;

@RepositoryRestResource
public interface GroupRepository extends JpaRepository<Group, String>, RevisionRepository<Group, String, Long> {
  default List<TailormapAdditionalProperty> findAdditionalPropertiesByGroups(List<String> groups) {
    return this.findAllById(groups).stream()
        .map(Group::getAdditionalProperties)
        .filter(Objects::nonNull)
        .flatMap(List::stream)
        .map(p -> new TailormapAdditionalProperty(p.getKey(), p.getIsPublic(), p.getValue()))
        .toList();
  }

  @Query(
      "select g.aliasForGroup from Group g where g.name in :groups and g.aliasForGroup is not null and g.aliasForGroup <> ''")
  Set<String> findAliasesForGroups(@Param("groups") Collection<String> groups);
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository;

import java.util.Optional;
import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorizeAdmin
@RepositoryRestResource(
    path = "feature-sources",
    collectionResourceRel = "feature-sources",
    itemResourceRel = "feature-source")
public interface FeatureSourceRepository extends JpaRepository<TMFeatureSource, Long> {
  TMFeatureSource findByUrl(String url);

  @Override
  @PreAuthorize(value = "permitAll()")
  @NonNull
  Optional<TMFeatureSource> findById(@NonNull Long id);

  @PreAuthorize(value = "permitAll()")
  Optional<TMFeatureSource> findByLinkedServiceId(Long id);
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Optional;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorizeAdmin
@RepositoryRestResource(
    path = "geo-services",
    collectionResourceRel = "geo-services",
    itemResourceRel = "geo-service")
public interface GeoServiceRepository extends JpaRepository<GeoService, Long> {
  @PreAuthorize("permitAll()")
  @Override
  @NonNull
  Optional<GeoService> findById(@NonNull Long aLong);

  @PreAuthorize("permitAll()")
  GeoService findByName(String name);
}

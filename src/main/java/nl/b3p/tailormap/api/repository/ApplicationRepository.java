/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@PreAuthorizeAdmin
public interface ApplicationRepository extends JpaRepository<Application, Long> {
  @PreAuthorize("permitAll()")
  Application findByName(String name);

  @PreAuthorize("permitAll()")
  @Override
  @NonNull
  Optional<Application> findById(@NonNull Long aLong);

  @Transactional
  @Modifying
  @Query(value = "update Application a set a.authenticatedRequired = :required  where a.id = :id")
  void setAuthenticatedRequired(Long id, Boolean required);
}

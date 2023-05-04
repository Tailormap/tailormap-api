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
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorizeAdmin
public interface ApplicationRepository extends JpaRepository<Application, Long> {
  @PreAuthorize("permitAll()")
  Application findByName(String name);

  @PreAuthorize("permitAll()")
  @Override
  @NonNull
  Optional<Application> findById(@NonNull Long aLong);
}

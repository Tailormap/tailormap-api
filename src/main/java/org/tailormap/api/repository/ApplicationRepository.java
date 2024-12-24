/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.security.annotation.PreAuthorizeAdmin;

@PreAuthorizeAdmin
public interface ApplicationRepository extends JpaRepository<Application, Long> {
  @PreAuthorize("permitAll()")
  Application findByName(String name);

  @PreAuthorize("permitAll()")
  @Override
  @NonNull Optional<Application> findById(@NonNull Long aLong);
}

/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
  Application findByName(String name);

  @Transactional
  @Modifying
  @Query(value = "update Application a set a.authenticatedRequired = :required  where a.id = :id")
  void setAuthenticatedRequired(Long id, Boolean required);
}

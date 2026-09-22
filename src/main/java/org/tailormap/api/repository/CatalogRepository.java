/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.data.repository.query.Param;
import org.tailormap.api.persistence.Catalog;
import org.tailormap.api.security.annotation.PreAuthorizeAdmin;

@PreAuthorizeAdmin
public interface CatalogRepository extends JpaRepository<Catalog, String>, RevisionRepository<Catalog, String, Long> {
  /**
   * Find a catalog by id with a pessimistic write lock. This will prevent other transactions from modifying the
   * catalog until the current transaction is complete.
   *
   * @param id the id of the catalog
   * @return an Optional containing the catalog if found, or empty if not found
   */
  @Query("SELECT c FROM Catalog c WHERE c.id = :id")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Catalog> findByIdWithLock(@Param("id") String id);
}

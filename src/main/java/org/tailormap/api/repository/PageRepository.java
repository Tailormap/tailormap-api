/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.Page;

@RepositoryRestResource(path = "pages", collectionResourceRel = "pages", itemResourceRel = "page")
public interface PageRepository extends JpaRepository<Page, Long> {
  @PreAuthorize("permitAll()")
  Page findByName(String name);
}

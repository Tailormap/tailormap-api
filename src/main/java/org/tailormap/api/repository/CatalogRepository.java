/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tailormap.api.persistence.Catalog;
import org.tailormap.api.security.annotation.PreAuthorizeAdmin;

@PreAuthorizeAdmin
public interface CatalogRepository extends JpaRepository<Catalog, String> {}

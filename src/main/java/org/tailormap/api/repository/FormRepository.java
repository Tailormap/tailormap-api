/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.tailormap.api.persistence.Form;

@RepositoryRestResource(path = "forms", collectionResourceRel = "forms", itemResourceRel = "form")
public interface FormRepository extends JpaRepository<Form, Long> {}

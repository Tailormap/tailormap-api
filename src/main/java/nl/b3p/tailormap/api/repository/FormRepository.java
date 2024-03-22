/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.Form;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(path = "forms", collectionResourceRel = "forms", itemResourceRel = "form")
public interface FormRepository extends JpaRepository<Form, Long> {}

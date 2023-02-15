/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Collection;
import java.util.List;

import nl.b3p.tailormap.api.persistence.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import javax.validation.constraints.NotNull;

@RepositoryRestResource
public interface UserRepository extends JpaRepository<User, Long> {

  @EntityGraph(attributePaths = {"groups"})
  User findByUsername(String username);

  boolean existsByGroupsNameIn(Collection<String> groupNames);
}

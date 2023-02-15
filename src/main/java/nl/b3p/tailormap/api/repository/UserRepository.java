/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Collection;
import nl.b3p.tailormap.api.persistence.User;
import nl.b3p.tailormap.api.security.annotation.PreAuthorizeAdmin;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorizeAdmin
@RepositoryRestResource
public interface UserRepository extends JpaRepository<User, Long> {

  @PreAuthorize("permitAll()")
  @EntityGraph(attributePaths = {"groups"})
  User findByUsername(String username);

  boolean existsByGroupsNameIn(Collection<String> groupNames);
}

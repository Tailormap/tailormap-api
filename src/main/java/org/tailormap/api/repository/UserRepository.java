/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.User;
import org.tailormap.api.security.annotation.PreAuthorizeAdmin;

@PreAuthorizeAdmin
@RepositoryRestResource
public interface UserRepository extends JpaRepository<User, String> {
  @Override
  @PreAuthorize("permitAll()")
  @EntityGraph(attributePaths = {"groups"})
  @NonNull Optional<User> findById(@NonNull String username);

  boolean existsByGroupsNameIn(Collection<String> groupNames);

  @PreAuthorize("permitAll()")
  Optional<User> findByEmail(@NonNull String email);

  @PreAuthorize("permitAll()")
  @Modifying
  @Query("update User u set u.password= :password where u.username = :username")
  void updatePassword(@NotNull @Param("username") String username, @NotNull @Param("password") String password);
}

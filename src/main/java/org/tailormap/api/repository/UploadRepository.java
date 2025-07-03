/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.Upload;

public interface UploadRepository extends JpaRepository<Upload, UUID> {
  @PreAuthorize("permitAll()")
  @NonNull @Query("select lastModified from Upload where id = :id")
  Optional<OffsetDateTime> findLastModifiedById(@NonNull UUID id);

  @PreAuthorize("permitAll()")
  @NonNull Optional<Upload> findByIdAndCategory(@NonNull UUID id, @NonNull String category);

  @PreAuthorize("permitAll()")
  @NonNull @EntityGraph(attributePaths = {"content"})
  Optional<Upload> findWithContentByIdAndCategory(@NonNull UUID id, @NonNull String category);

  @PreAuthorize(value = "permitAll()")
  List<Upload> findByCategory(String category);

  @PreAuthorize("permitAll()")
  @NonNull @EntityGraph(attributePaths = {"content"})
  // Find the most recent upload for a specific category with its content
  Optional<Upload> findFirstWithContentByCategoryOrderByLastModifiedDesc(@NonNull String category);

  @PreAuthorize("permitAll()")
  @Query(
      "select new org.tailormap.api.repository.UploadMd5Match(u.id, u.content) from Upload u where u.category = :category and function('md5', u.content) in :hashes")
  List<UploadMd5Match> findByContentMd5In(@NonNull String category, @NonNull List<String> hashes);
}

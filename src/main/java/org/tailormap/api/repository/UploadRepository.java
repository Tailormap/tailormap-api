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
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.history.RevisionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.tailormap.api.persistence.Upload;
import org.tailormap.api.persistence.UploadCategory;

public interface UploadRepository extends JpaRepository<Upload, UUID>, RevisionRepository<Upload, UUID, Long> {
  @PreAuthorize("permitAll()")
  @NonNull @Query("select lastModified from Upload where id = :id")
  Optional<OffsetDateTime> findLastModifiedById(@NonNull UUID id);

  @PreAuthorize("permitAll()")
  @NonNull Optional<Upload> findByIdAndCategory(@NonNull UUID id, @NonNull UploadCategory category);

  @PreAuthorize("permitAll()")
  @NonNull @EntityGraph(attributePaths = {"content"})
  Optional<Upload> findWithContentByCategoryAndFilename(@NonNull UploadCategory category, @NonNull String filename);

  @PreAuthorize("permitAll()")
  @NonNull @EntityGraph(attributePaths = {"content"})
  Optional<Upload> findWithContentByIdAndCategory(@NonNull UUID id, @NonNull UploadCategory category);

  @PreAuthorize(value = "permitAll()")
  List<Upload> findByCategory(@NonNull UploadCategory category);

  @PreAuthorize("permitAll()")
  @NonNull @EntityGraph(attributePaths = {"content"})
  // Find the most recent upload for a specific category with its content
  Optional<Upload> findFirstWithContentByCategoryOrderByLastModifiedDesc(@NonNull UploadCategory category);

  @PreAuthorize("permitAll()")
  @Query(
      "select new org.tailormap.api.repository.UploadMatch(u.id, u.hash) from Upload u where u.category = :category and u.hash in :hashes")
  List<UploadMatch> findByHashIn(@NonNull UploadCategory category, @NonNull List<String> hashes);

  @PreAuthorize("permitAll()")
  List<Upload> findByFilename(String filename);

  /** get all uploads with the given ids, including their content, e.g. for downloading as a zip. */
  @PreAuthorize("permitAll()")
  @NonNull @EntityGraph(attributePaths = {"content"})
  List<Upload> findAllWithContentByIdIn(@NonNull List<UUID> ids);
}

/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nl.b3p.tailormap.api.persistence.Upload;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

public interface UploadRepository extends JpaRepository<Upload, UUID> {
  @PreAuthorize("permitAll()")
  @NonNull
  @Query("select lastModified from Upload where id = :id")
  Optional<OffsetDateTime> findLastModifiedById(@NonNull UUID id);

  @PreAuthorize("permitAll()")
  @NonNull
  Optional<Upload> findByIdAndCategory(@NonNull UUID id, @NonNull String category);

  @PreAuthorize("permitAll()")
  @NonNull
  @EntityGraph(attributePaths = {"content"})
  Optional<Upload> findWithContentByIdAndCategory(@NonNull UUID id, @NonNull String category);

  @PreAuthorize(value = "permitAll()")
  List<Upload> findByCategory(String category);
}

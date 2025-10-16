/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.repository;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.tailormap.api.persistence.TemporaryToken;

public interface TemporaryTokenRepository extends JpaRepository<TemporaryToken, UUID> {
  long countByUsername(@NotNull String username);
}

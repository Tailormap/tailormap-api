/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.TMFeatureSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureSourceRepository extends JpaRepository<TMFeatureSource, Long> {
  TMFeatureSource findByUrl(String url);
}

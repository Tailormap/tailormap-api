/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.repository;

import nl.b3p.tailormap.api.persistence.FeatureSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeatureSourceRepository extends JpaRepository<FeatureSource, Long> {
  FeatureSource findByUrl(String url);
}

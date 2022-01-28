/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.tailormap.viewer.config.app.ApplicationLayer;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Easy to use methods to access {@link ApplicationLayer}.
 *
 * @since 0.1
 */
public interface ApplicationLayerRepository extends JpaRepository<ApplicationLayer, Long> {}

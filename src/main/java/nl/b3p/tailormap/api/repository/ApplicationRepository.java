/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.tailormap.viewer.config.app.Application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Easy to use methods to access {@link Application}.
 *
 * @since 0.1
 */
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByName(String name);

    Application findByNameAndVersion(String name, String version);
}

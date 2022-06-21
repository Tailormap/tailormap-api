/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Easy to use methods to access {@link Layer}.
 *
 * @since 0.1
 */
public interface LayerRepository extends JpaRepository<Layer, Long> {
    public Layer getByServiceAndName(GeoService service, String name);

    @EntityGraph(attributePaths = {"readers", "details"})
    List<Layer> findByServiceIdIn(Iterable<Long> list);
}

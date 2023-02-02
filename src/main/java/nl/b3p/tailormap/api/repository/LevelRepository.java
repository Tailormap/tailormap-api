/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.repository;

import java.util.Collection;
import java.util.List;
import nl.tailormap.viewer.config.app.Level;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Easy to use methods to access {@link Level}.
 *
 * @since 0.1
 */
public interface LevelRepository extends JpaRepository<Level, Long> {
  @Query(
      value =
          "select * from level_ where id in (with recursive level_tree(id) as (select id from level_ where id = ?1 union all select l.id from level_tree, level_ l where l.parent = level_tree.id) select * from level_tree)",
      nativeQuery = true)
  List<Level> findByLevelTree(Long rootId);

  @EntityGraph(attributePaths = {"readers", "layers.service", "layers.readers"})
  List<Level> findWithAuthorizationDataByIdIn(Collection<Long> id);
}

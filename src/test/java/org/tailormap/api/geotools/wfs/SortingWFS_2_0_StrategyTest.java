/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.geotools.api.filter.FilterFactory;
import org.geotools.api.filter.sort.SortBy;
import org.geotools.api.filter.sort.SortOrder;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.jupiter.api.Test;

class SortingWFS_2_0_StrategyTest {
  private static final FilterFactory ff = CommonFactoryFinder.getFilterFactory();

  private static SortBy[] sortBy(SortBy... sortBy) {
    return sortBy;
  }

  @Test
  void server_sort_by_without_natural_or_reverse_order() {
    SortBy code = ff.sort("code", SortOrder.ASCENDING);
    assertEquals(List.of(code), SortingWFS_2_0_Strategy.getServerSortBy(sortBy(SortBy.NATURAL_ORDER, code)));
    assertTrue(SortingWFS_2_0_Strategy.getServerSortBy(sortBy(SortBy.NATURAL_ORDER))
        .isEmpty());
    assertTrue(SortingWFS_2_0_Strategy.getServerSortBy(sortBy(SortBy.REVERSE_ORDER))
        .isEmpty());
    assertTrue(SortingWFS_2_0_Strategy.getServerSortBy(null).isEmpty());
  }

  @Test
  void sort_by_parameter() {
    assertEquals(
        "naam ASC", SortingWFS_2_0_Strategy.toSortByParameter(List.of(ff.sort("naam", SortOrder.ASCENDING))));
    assertEquals(
        "naam DESC,code ASC",
        SortingWFS_2_0_Strategy.toSortByParameter(
            List.of(ff.sort("naam", SortOrder.DESCENDING), ff.sort("code", SortOrder.ASCENDING))));
  }
}

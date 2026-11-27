/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.opengis.wfs20.GetFeatureType;
import net.opengis.wfs20.QueryType;
import org.eclipse.emf.ecore.EObject;
import org.geotools.api.filter.sort.SortBy;
import org.geotools.api.filter.sort.SortOrder;
import org.geotools.data.wfs.internal.GetFeatureRequest;
import org.geotools.data.wfs.internal.v2_0.StrictWFS_2_0_Strategy;

/**
 * {@link StrictWFS_2_0_Strategy} that sends the sort order of the query to the server, as {@code SORTBY} for GET and
 * {@code fes:SortBy} for POST. GeoTools 35.2 leaves this out (it is commented out in {@link StrictWFS_2_0_Strategy}),
 * so with paging each page is sorted on its own in memory, see {@link SortingWFSClient}.
 *
 * <p>{@link SortBy#NATURAL_ORDER} (no property name) is not sent: with no sort order the server uses its own (natural)
 * order. {@code ContentFeatureSource} adds {@code NATURAL_ORDER} itself when paging without a sort order, for stable
 * paging. {@link SortBy#REVERSE_ORDER} can't be done by a WFS and is an error.
 *
 * <p>Sorting on an attribute when the server doesn't implement sorting (no {@code ImplementsSorting} constraint) is an
 * error. {@link SortingWFSClient} says it can sort, so GeoTools doesn't sort in memory, which is wrong with paging.
 *
 * <p>This can be removed when GeoTools sends the sort order for WFS 2.0.0 itself.
 */
public class SortingWFS_2_0_Strategy extends StrictWFS_2_0_Strategy {

  private boolean implementsSorting;

  /**
   * Set whether the server implements sorting.
   *
   * @param implementsSorting whether the capabilities have the {@code ImplementsSorting} constraint set to TRUE
   */
  public void setImplementsSorting(boolean implementsSorting) {
    this.implementsSorting = implementsSorting;
  }

  /**
   * Get the sort order to send to the server: without the entry for natural order.
   *
   * @param sortBy the sort order of the GetFeature request, may be null
   * @param implementsSorting whether the server implements sorting
   * @return the sort order to send to the server, empty if none
   * @throws IllegalArgumentException for {@link SortBy#REVERSE_ORDER}, or when sorting on an attribute and the server
   *     doesn't implement sorting
   */
  static List<SortBy> getServerSortBy(SortBy[] sortBy, boolean implementsSorting) {
    if (sortBy == null) {
      return List.of();
    }
    List<SortBy> serverSortBy = new ArrayList<>();
    for (SortBy s : sortBy) {
      if (s == null || (s.getPropertyName() == null && SortOrder.ASCENDING.equals(s.getSortOrder()))) {
        // Natural order
        continue;
      }
      if (s.getPropertyName() == null) {
        throw new IllegalArgumentException("Sorting in reverse natural order is not possible with WFS");
      }
      serverSortBy.add(s);
    }
    if (!serverSortBy.isEmpty() && !implementsSorting) {
      throw new IllegalArgumentException("This WFS does not support sorting");
    }
    return serverSortBy;
  }

  /**
   * Format a sort order as the value of the {@code SORTBY} KVP parameter for WFS 2.0.0, for example {@code name
   * ASC,code DESC}.
   *
   * @param sortBy the sort order, not empty
   * @return the SORTBY parameter value
   */
  static String toSortByParameter(List<SortBy> sortBy) {
    return sortBy.stream()
        .map(s -> s.getPropertyName().getPropertyName()
            + (SortOrder.DESCENDING.equals(s.getSortOrder()) ? " DESC" : " ASC"))
        .collect(Collectors.joining(","));
  }

  @Override
  protected Map<String, String> buildGetFeatureParametersForGET(GetFeatureRequest request) {
    Map<String, String> kvp = super.buildGetFeatureParametersForGET(request);
    List<SortBy> sortBy = getServerSortBy(request.getSortBy(), implementsSorting);
    if (!sortBy.isEmpty()) {
      if (request.isStoredQuery()) {
        throw new IllegalArgumentException("Sorting a stored query is not possible");
      }
      kvp.put("SORTBY", toSortByParameter(sortBy));
    }
    return kvp;
  }

  @Override
  protected EObject createGetFeatureRequestPost(GetFeatureRequest request) {
    EObject getFeature = super.createGetFeatureRequestPost(request);
    List<SortBy> sortBy = getServerSortBy(request.getSortBy(), implementsSorting);
    if (!sortBy.isEmpty()) {
      if (request.isStoredQuery()) {
        throw new IllegalArgumentException("Sorting a stored query is not possible");
      }
      if (getFeature instanceof GetFeatureType getFeatureType) {
        for (Object query : getFeatureType.getAbstractQueryExpression()) {
          if (query instanceof QueryType queryType) {
            queryType.getSortBy().addAll(sortBy);
          }
        }
      }
    }
    return getFeature;
  }
}

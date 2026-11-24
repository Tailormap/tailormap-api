/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.util.Arrays;
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
 * <p>{@link SortBy#NATURAL_ORDER} and {@link SortBy#REVERSE_ORDER} (no property name) are not sent: with no sort order
 * the server uses its own (natural) order. {@code ContentFeatureSource} adds {@code NATURAL_ORDER} itself when paging
 * without a sort order, for stable paging.
 *
 * <p>This can be removed when GeoTools sends the sort order for WFS 2.0.0 itself.
 */
public class SortingWFS_2_0_Strategy extends StrictWFS_2_0_Strategy {

  /**
   * Get the sort order without the entries for natural or reverse order.
   *
   * @param sortBy the sort order of the GetFeature request, may be null
   * @return the sort order to send to the server, empty if none
   */
  static List<SortBy> getServerSortBy(SortBy[] sortBy) {
    if (sortBy == null) {
      return List.of();
    }
    return Arrays.stream(sortBy)
        .filter(s -> s != null && s.getPropertyName() != null)
        .toList();
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
    List<SortBy> sortBy = getServerSortBy(request.getSortBy());
    if (!request.isStoredQuery() && !sortBy.isEmpty()) {
      kvp.put("SORTBY", toSortByParameter(sortBy));
    }
    return kvp;
  }

  @Override
  protected EObject createGetFeatureRequestPost(GetFeatureRequest request) {
    EObject getFeature = super.createGetFeatureRequestPost(request);
    List<SortBy> sortBy = getServerSortBy(request.getSortBy());
    if (!sortBy.isEmpty() && getFeature instanceof GetFeatureType getFeatureType) {
      for (Object query : getFeatureType.getAbstractQueryExpression()) {
        if (query instanceof QueryType queryType) {
          queryType.getSortBy().addAll(sortBy);
        }
      }
    }
    return getFeature;
  }
}

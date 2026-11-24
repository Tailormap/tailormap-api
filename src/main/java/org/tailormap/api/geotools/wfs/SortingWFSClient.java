/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import net.opengis.fes20.FilterCapabilitiesType;
import net.opengis.ows11.DomainType;
import net.opengis.wfs20.WFSCapabilitiesType;
import org.geotools.data.wfs.internal.WFSClient;
import org.geotools.data.wfs.internal.WFSConfig;
import org.geotools.data.wfs.internal.WFSGetCapabilities;
import org.geotools.data.wfs.internal.v2_0.StrictWFS_2_0_Strategy;
import org.geotools.http.HTTPClient;
import org.geotools.ows.ServiceException;

/**
 * {@link WFSClient} that lets a WFS 2.0.0 server sort, when the capabilities have the {@code ImplementsSorting}
 * constraint. GeoTools 35.2 only lets a WFS 1.1.0 server sort.
 *
 * <p>When the client can't sort, {@code ContentFeatureSource} sorts each page of features in memory after getting it
 * from the server with {@code STARTINDEX} and {@code COUNT}. Each page is sorted on its own, which gives a wrong order
 * across pages, and it fails when a feature has an attribute that isn't {@code Serializable}, such as an
 * {@code Instant} for a date. That also happens without a sort order, because {@code ContentFeatureSource} adds
 * {@code NATURAL_ORDER} when paging.
 *
 * <p>For WFS 2.0.0 the {@link StrictWFS_2_0_Strategy} GeoTools picks is replaced with {@link SortingWFS_2_0_Strategy},
 * which sends the sort order to the server. Other strategies (such as for ArcGIS) are left alone and don't sort on the
 * server.
 *
 * <p>This can be removed when GeoTools supports sorting for WFS 2.0.0 itself.
 */
public class SortingWFSClient extends WFSClient {
  private final boolean canSort;

  public SortingWFSClient(URL capabilitiesURL, HTTPClient httpClient, WFSConfig config)
      throws IOException, ServiceException {
    super(capabilitiesURL, httpClient, config);

    // The strategy is chosen in a private method in the WFSClient constructor, so replace it afterwards. Only
    // replace the exact class, not subclasses such as ArcGisStrategy_2_0.
    if (getStrategy().getClass() == StrictWFS_2_0_Strategy.class) {
      SortingWFS_2_0_Strategy strategy = new SortingWFS_2_0_Strategy();
      strategy.setConfig(config);
      strategy.setCapabilities(getCapabilities());
      super.specification = strategy;
      canSort = implementsSorting(getCapabilities());
    } else {
      canSort = super.canSort();
    }
  }

  /**
   * Check whether WFS 2.0.0 capabilities have the {@code ImplementsSorting} conformance constraint set to
   * {@code TRUE}. This is a constraint in {@code fes:Filter_Capabilities/fes:Conformance}, not in
   * {@code ows:OperationsMetadata}.
   *
   * @param capabilities the capabilities
   * @return whether the server implements sorting
   */
  static boolean implementsSorting(WFSGetCapabilities capabilities) {
    if (!(capabilities.getParsedCapabilities() instanceof WFSCapabilitiesType caps)) {
      return false;
    }
    FilterCapabilitiesType filterCapabilities = caps.getFilterCapabilities();
    if (filterCapabilities == null || filterCapabilities.getConformance() == null) {
      return false;
    }
    for (DomainType constraint : filterCapabilities.getConformance().getConstraint()) {
      if ("ImplementsSorting".equals(constraint.getName())) {
        return constraint.getDefaultValue() != null
            && constraint.getDefaultValue().getValue() != null
            && "TRUE"
                .equals(constraint
                    .getDefaultValue()
                    .getValue()
                    .trim()
                    .toUpperCase(Locale.ROOT));
      }
    }
    return false;
  }

  @Override
  public boolean canSort() {
    return canSort;
  }
}

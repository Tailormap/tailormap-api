/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.geotools.wfs;

import java.util.List;

public record SimpleWFSLayerDescription(String wfsUrl, List<String> typeNames) {

  public String getFirstTypeName() {
    return typeNames.get(0);
  }
}

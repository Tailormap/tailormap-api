/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

public interface Constants {
  String FID = "__fid";
  int DEFAULT_MAX_FEATURES = 10;

  String NAME_REGEX = "^[a-zA-Z0-9-_]+";
  String NAME_REGEX_INVALID_MESSAGE =
      "name must consist of alphanumeric characters, underscore or -";

  String SEARCH_LAYER = "searchLayer";
  String LAYER_NAME_QUERY = SEARCH_LAYER + ":";
  String INDEX_SEARCH_FIELD = "searchFields";
  String INDEX_DISPLAY_FIELD = "displayFields";
  String INDEX_GEOM_FIELD = "geometry";
}

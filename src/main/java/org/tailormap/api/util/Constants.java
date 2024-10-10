/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

public interface Constants {
  String FID = "__fid";
  String ID = "id";
  String NAME_REGEX = "^[a-zA-Z0-9-_]+";
  String NAME_REGEX_INVALID_MESSAGE =
      "name must consist of alphanumeric characters, underscore or -";
  String SEARCH_ID_FIELD = ID;
  String SEARCH_LAYER = "searchLayer";
  String INDEX_SEARCH_FIELD = "searchFields";
  String INDEX_DISPLAY_FIELD = "displayFields";
  String INDEX_GEOM_FIELD = "geometry";

  String UUID_REGEX = "(?i)^[\\da-f]{8}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{4}-[\\da-f]{12}$";

  String TEST_TASK_TYPE = "poc";
}

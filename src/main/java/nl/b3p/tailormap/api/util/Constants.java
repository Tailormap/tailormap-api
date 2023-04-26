/*
 * Copyright (C) 2021 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

public interface Constants {
  String FID = "__fid";
  int DEFAULT_MAX_FEATURES = 10;

  String NAME_REGEX = "^[a-zA-Z0-9-]+";
  String NAME_REGEX_INVALID_MESSAGE = "name must consist of alphanumeric characters or -";
}

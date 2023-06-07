/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.util;

public class TMStringUtils {
  public static String nullIfEmpty(String s) {
    return s != null && s.isEmpty() ? null : s;
  }
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.util;

import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class TMExceptionUtils {
  public static String joinAllThrowableMessages(Throwable t) {
    return joinAllThrowableMessages(t, ", cause: ");
  }

  public static String joinAllThrowableMessages(Throwable t, String delimiter) {
    return ExceptionUtils.getThrowableList(t).stream()
        .map(ExceptionUtils::getMessage)
        .collect(Collectors.joining(delimiter));
  }
}

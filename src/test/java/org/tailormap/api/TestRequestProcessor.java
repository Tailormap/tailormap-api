/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

public class TestRequestProcessor {
  public static RequestPostProcessor setServletPath(String servletPath) {
    return request -> {
      // Required for AppRestControllerAdvice.populateViewerKind()
      request.setServletPath(servletPath);
      return request;
    };
  }
}

/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration.base;

import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SourceMapSecurityFilter implements Filter {

  @Value("${tailormap-api.source-map.auth:#null}")
  private String sourceMapAuth;

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
      throws ServletException, IOException {

    if (!"public".equals(sourceMapAuth)) {
      HttpServletRequest request = (HttpServletRequest) servletRequest;
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      String path = request.getRequestURI().substring(request.getContextPath().length());
      if (!path.startsWith("/api/") && path.endsWith(".map")) {
        if (sourceMapAuth == null) {
          response.sendError(SC_FORBIDDEN);
          return;
        }
        String sourceMapAuthorization =
            "Basic "
                + Base64.getEncoder()
                    .encodeToString(sourceMapAuth.getBytes(StandardCharsets.UTF_8));
        if (!sourceMapAuthorization.equals(request.getHeader("Authorization"))) {
          response.addHeader("WWW-Authenticate", "Basic realm=\"Source maps\"");
          response.sendError(SC_UNAUTHORIZED);
          return;
        }
      }
    }

    chain.doFilter(servletRequest, servletResponse);
  }
}

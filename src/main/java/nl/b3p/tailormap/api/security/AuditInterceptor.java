/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** AuditInterceptor logs the user that is making the request. */
public class AuditInterceptor extends OncePerRequestFilter {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (logger.isDebugEnabled()) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof AnonymousAuthenticationToken) {
        logger.debug("Audit: request by anonymous user ({})", authentication.getAuthorities());
      }
      if (authentication instanceof UsernamePasswordAuthenticationToken) {
        logger.debug(
            "Audit: request by registered user: {}, authorities: {}",
            authentication.getName(),
            authentication.getAuthorities());
      }
    }
    filterChain.doFilter(request, response);
  }
}

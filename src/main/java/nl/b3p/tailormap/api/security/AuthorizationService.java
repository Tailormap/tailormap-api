/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import java.util.List;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRule;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRuleDecision;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRuleDecisionsValue;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Validates access control rules. Any call to mayUserRead will verify that the currently logged in
 * user is not only allowed to read the current object, but any objcet above and below it in the
 * hierarchy.
 */
@Service
public class AuthorizationService {
  private Optional<AuthorizationRuleDecision> isAuthorizedByRules(
      List<AuthorizationRule> rules, String type) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    for (AuthorizationRule rule : rules) {
      boolean matchesGroup =
          auth.getAuthorities().stream()
              .anyMatch(x -> x.getAuthority().equals(rule.getGroupName()));
      if (!matchesGroup) {
        continue;
      }

      AuthorizationRuleDecisionsValue value = rule.getDecisions().get(type);
      if (value == null) {
        return Optional.empty();
      }

      return Optional.of(value.getDecision());
    }

    return Optional.empty();
  }

  /**
   * Verifies that this user may read this Application.
   *
   * @param application the Application to check
   * @return the results from the access control checks.
   */
  public boolean mayUserRead(Application application) {
    if (!application.isAuthenticatedRequired()) {
      return true;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
      return false;
    }

    return isAuthorizedByRules(application.getAuthorizationRules(), "read")
        .equals(Optional.of(AuthorizationRuleDecision.ALLOW));
  }

  /**
   * Verifies that this user may read this GeoService.
   *
   * @param geoService the GeoService to check
   * @return the results from the access control checks.
   */
  public boolean mayUserRead(GeoService geoService) {
    return isAuthorizedByRules(geoService.getAuthorizationRules(), "read")
        .equals(Optional.of(AuthorizationRuleDecision.ALLOW));
  }
}

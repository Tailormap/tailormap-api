/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.GeoService;
import nl.b3p.tailormap.api.persistence.Group;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRule;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRuleDecision;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Validates access control rules. Any call to mayUserRead will verify that the currently logged in
 * user is not only allowed to read the current object, but any object above and below it in the
 * hierarchy.
 */
@Service
public class AuthorizationService {
  public static final String ACCESS_TYPE_READ = "read";

  private Optional<AuthorizationRuleDecision> isAuthorizedByRules(
      List<AuthorizationRule> rules, String type) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    Set<String> groups;

    if (auth == null || auth instanceof AnonymousAuthenticationToken) {
      groups = Set.of(Group.ANONYMOUS);
    } else {
      groups = new HashSet<>();
      groups.add(Group.ANONYMOUS);
      groups.add(Group.AUTHENTICATED);

      for (GrantedAuthority authority : auth.getAuthorities()) {
        groups.add(authority.getAuthority());
      }
    }

    // Admins are allowed access to anything.
    if (groups.contains(Group.ADMIN)) {
      return Optional.of(AuthorizationRuleDecision.ALLOW);
    }

    boolean hasValidRule = false;

    for (AuthorizationRule rule : rules) {
      boolean matchesGroup = groups.contains(rule.getGroupName());
      if (!matchesGroup) {
        continue;
      }

      hasValidRule = true;

      AuthorizationRuleDecision value = rule.getDecisions().get(type);
      if (value == null) {
        return Optional.empty();
      }

      if (value.equals(AuthorizationRuleDecision.ALLOW)) {
        return Optional.of(value);
      }
    }

    if (hasValidRule) {
      return Optional.of(AuthorizationRuleDecision.DENY);
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
    return isAuthorizedByRules(application.getAuthorizationRules(), ACCESS_TYPE_READ)
        .equals(Optional.of(AuthorizationRuleDecision.ALLOW));
  }

  /**
   * Verifies that this user may read this GeoService.
   *
   * @param geoService the GeoService to check
   * @return the results from the access control checks.
   */
  public boolean mayUserRead(GeoService geoService) {
    return isAuthorizedByRules(geoService.getAuthorizationRules(), ACCESS_TYPE_READ)
        .equals(Optional.of(AuthorizationRuleDecision.ALLOW));
  }

  /**
   * Verifies that this user may read the Layer in context of the GeoService.
   *
   * @param geoService the GeoService to check
   * @param layer the GeoServiceLayer to check
   * @return the results from the access control checks.
   */
  public boolean mayUserRead(GeoService geoService, GeoServiceLayer layer) {
    Optional<AuthorizationRuleDecision> geoserviceDecision =
        isAuthorizedByRules(geoService.getAuthorizationRules(), ACCESS_TYPE_READ);

    if (geoserviceDecision.equals(Optional.of(AuthorizationRuleDecision.DENY))) {
      return false;
    }

    GeoServiceLayerSettings settings =
        geoService.getSettings().getLayerSettings().get(layer.getName());
    if (settings != null && settings.getAuthorizationRules() != null) {
      Optional<AuthorizationRuleDecision> decision =
          isAuthorizedByRules(settings.getAuthorizationRules(), ACCESS_TYPE_READ);
      // If no authorization rules are present, fall back to GeoService authorization.
      if (decision.isPresent() || !settings.getAuthorizationRules().isEmpty()) {
        return decision.equals(Optional.of(AuthorizationRuleDecision.ALLOW));
      }
    }

    return geoserviceDecision.equals(Optional.of(AuthorizationRuleDecision.ALLOW));
  }

  /**
   * To avoid exposing a secured service by proxying it to everyone, do not proxy a secured geo
   * service when the application is public (accessible by anonymous users). Do not even allow
   * proxying a secured service if the user is logged viewing a public app!
   *
   * @param application The application
   * @param geoService The geo service
   * @return Whether to allow proxying this service for the application
   */
  public boolean allowProxyAccess(Application application, GeoService geoService) {
    if (geoService.getAuthentication() == null) {
      return true;
    }
    return application.getAuthorizationRules().stream()
        .noneMatch(
            rule ->
                Group.ANONYMOUS.equals(rule.getGroupName())
                    && AuthorizationRuleDecision.ALLOW.equals(
                        rule.getDecisions().get(ACCESS_TYPE_READ)));
  }
}

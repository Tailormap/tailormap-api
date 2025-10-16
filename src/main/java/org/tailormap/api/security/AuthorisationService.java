/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.security;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.Group;
import org.tailormap.api.persistence.json.AuthorizationRule;
import org.tailormap.api.persistence.json.AuthorizationRuleDecision;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.GeoServiceLayerSettings;

/**
 * Validates access control rules. Any call to userAllowedToViewApplication will verify that the currently logged-in
 * user is not only allowed to read the current object, but any object above and below it in the hierarchy.
 */
@Service
public class AuthorisationService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final String ACCESS_TYPE_VIEW = "read";

  private Optional<AuthorizationRuleDecision> isAuthorizedByRules(List<AuthorizationRule> rules) {
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
    logger.trace("Groups to check rules against: {}", groups);

    // Admins are allowed access to anything.
    if (groups.contains(Group.ADMIN)) {
      logger.trace(
          "Returning {} because {} is allowed access to anything.",
          AuthorizationRuleDecision.ALLOW,
          Group.ADMIN);
      return Optional.of(AuthorizationRuleDecision.ALLOW);
    }

    boolean hasValidRule = false;

    for (AuthorizationRule rule : rules) {
      if (logger.isTraceEnabled()) {
        logger.trace("Checking rule: \n{} against groups {}.", rule, groups);
      }

      boolean matchesGroup = groups.contains(rule.getGroupName());
      if (!matchesGroup) {
        continue;
      }

      hasValidRule = true;

      AuthorizationRuleDecision value = rule.getDecisions().get(AuthorisationService.ACCESS_TYPE_VIEW);
      if (value == null) {
        logger.trace(
            "No decision found for rule: \n{} and access: {}, returning <EMPTY>.",
            rule,
            AuthorisationService.ACCESS_TYPE_VIEW);
        return Optional.empty();
      }

      if (value.equals(AuthorizationRuleDecision.ALLOW)) {
        logger.trace(
            "Returning {} because rule: \n{} allows {} access for access: {}.",
            value,
            rule,
            rule.getGroupName(),
            AuthorisationService.ACCESS_TYPE_VIEW);
        return Optional.of(value);
      }
    }

    if (hasValidRule) {
      logger.trace(
          "Returning {} because no valid rule allowed access for access: {}.",
          AuthorizationRuleDecision.DENY,
          AuthorisationService.ACCESS_TYPE_VIEW);
      return Optional.of(AuthorizationRuleDecision.DENY);
    }

    logger.trace(
        "Returning <EMPTY> because no rules matched for access: {}.", AuthorisationService.ACCESS_TYPE_VIEW);
    return Optional.empty();
  }

  /**
   * Verifies that the (authenticated) user may view/open the application.
   *
   * @param application the Application to check
   * @return the result from the access control checks.
   */
  public boolean userAllowedToViewApplication(Application application) {
    logger.trace(
        "Checking if user is allowed to view Application {} ({}).",
        application.getTitle(),
        application.getTitle());
    final boolean allowed = isAuthorizedByRules(application.getAuthorizationRules())
        .equals(Optional.of(AuthorizationRuleDecision.ALLOW));
    logger.trace(
        "User is{} allowed to view application: {} (isAuthorizedByRules={}).",
        allowed ? "" : " not",
        application.getName(),
        allowed);
    return allowed;
  }

  /**
   * Verifies that the (authenticated) user may view this geoService.
   *
   * @param geoService the GeoService to check
   * @return the result from the access control checks.
   */
  public boolean userAllowedToViewGeoService(GeoService geoService) {
    logger.trace(
        "Checking if user is allowed to view GeoService {} ({}).", geoService.getId(), geoService.getTitle());
    if (!mustDenyAccessForSecuredProxy(geoService)) {
      return false;
    }
    final boolean allowed = isAuthorizedByRules(geoService.getAuthorizationRules())
        .equals(Optional.of(AuthorizationRuleDecision.ALLOW));
    logger.trace(
        "User is{} allowed to view GeoService: {} (isAuthorizedByRules={}).",
        allowed ? "" : " not",
        geoService.getTitle(),
        allowed);
    return allowed;
  }

  /**
   * Verifies that the (authenticated) user may view the layer in context of the geoService.
   *
   * @param geoService the GeoService to check
   * @param layer the GeoServiceLayer to check
   * @return the result from the access control checks.
   */
  public boolean userAllowedToViewGeoServiceLayer(GeoService geoService, GeoServiceLayer layer) {
    logger.trace(
        "Checking if user is allowed to view GeoService '{}' and layer {} ({}).",
        geoService.getTitle(),
        layer.getName(),
        layer.getTitle());
    // check if user is allowed to view the geoService
    Optional<AuthorizationRuleDecision> geoserviceDecision =
        isAuthorizedByRules(geoService.getAuthorizationRules());
    if (geoserviceDecision.equals(Optional.of(AuthorizationRuleDecision.DENY))) {
      logger.trace("Viewing GeoService {} is denied for user.", geoService.getTitle());
      return false;
    }

    GeoServiceLayerSettings layerSettings =
        geoService.getSettings().getLayerSettings().get(layer.getName());
    if (layerSettings != null && layerSettings.getAuthorizationRules() != null) {
      logger.trace(
          "Checking layer settings rules for GeoService '{}' and layer '{}'. \nRules: {}",
          geoService.getTitle(),
          layer.getName(),
          layerSettings.getAuthorizationRules());
      List<AuthorizationRule> combinedRules = new ArrayList<>(geoService.getAuthorizationRules());
      for (AuthorizationRule rule : layerSettings.getAuthorizationRules()) {
        // replace any rule with the same group name, so we end up with a merged
        // set of rules where the layer rules override
        combinedRules.removeIf(r -> r.getGroupName().equals(rule.getGroupName()));
        combinedRules.add(rule);
      }
      logger.trace(
          "Combined rules for GeoService '{}' and layer '{}': \n{}",
          geoService.getTitle(),
          layer.getName(),
          combinedRules);

      Optional<AuthorizationRuleDecision> decision = isAuthorizedByRules(combinedRules);
      // If no authorization rules are present, fall back to geoService authorization.
      if (decision.isPresent() || !layerSettings.getAuthorizationRules().isEmpty()) {
        boolean allowed = decision.equals(Optional.of(AuthorizationRuleDecision.ALLOW));

        logger.trace(
            "Viewing GeoService '{}' and layer '{}' ({}) is {} for user.",
            geoService.getTitle(),
            layer.getName(),
            layer.getTitle(),
            (allowed ? "allowed" : "denied"));
        return allowed;
      }
    }

    boolean allowed = geoserviceDecision.equals(Optional.of(AuthorizationRuleDecision.ALLOW));
    logger.trace(
        "Viewing GeoService '{}' and layer '{}' ({}) is {} for user because service access is {3}.",
        geoService.getTitle(), layer.getName(), layer.getTitle(), (allowed ? "allowed" : "denied"));
    return allowed;
  }

  /**
   * To avoid exposing a secured service by proxying it to everyone, do not proxy a secured GeoService when the user
   * is not logged in.
   *
   * @param geoService The geo service to check
   * @return Whether to deny proxying this service
   */
  public boolean mustDenyAccessForSecuredProxy(GeoService geoService) {
    if (!Boolean.TRUE.equals(geoService.getSettings().getUseProxy())) {
      return false;
    }
    if (geoService.getAuthentication() == null) {
      return false;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return auth == null || auth instanceof AnonymousAuthenticationToken;
  }
}

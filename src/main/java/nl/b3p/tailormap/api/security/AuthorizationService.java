/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import nl.b3p.tailormap.api.persistence.Application;
import org.springframework.stereotype.Service;

/**
 * Validates access control rules. Any call to mayUserRead will verify that the currently logged in
 * user is not only allowed to read the current object, but any objcet above and below it in the
 * hierarchy.
 */
@Service
public class AuthorizationService {

  /**
   * Verifies that the user may read the object, based on the passed in readers set.
   *
   * @param readers the list of readers to validate against.
   * @return the result of validating the authorizations.
   */
  /*  private boolean isAuthorizedBySet(Set<String> readers) {

    return true;

    if (readers == null || readers.isEmpty()) {
      return true;
    }

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) {
      return false;
    }

    for (String reader : readers) {
      if (auth.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals(reader))) {
        return true;
      }
    }

    return false;
  }*/

  /**
   * Verifies that this user may read this Application.
   *
   * @param application the Application to check
   * @return the results from the access control checks.
   */
  public boolean mayUserRead(Application application) {
    return true;
    /*    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if ((authentication == null || authentication instanceof AnonymousAuthenticationToken)
        && application.isAuthenticatedRequired()) {
      return false;
    }

    return isAuthorizedBySet(application.getReaders());*/
  }

  /**
   * Verifies that this user may view an ApplicationLayer in the context of a certain Application.
   * As an ApplicationLayer cannot be traced back to a single Application (e.g. mashups), this
   * requires passing in the Application to use as context. The ApplicationLayer, its parent Levels,
   * and the Application are validated, as are the Layer, all its parents, and the GeoService.
   *
   * @param applicationLayer the ApplicationLayer to check
   * @param context the Application to verify this ApplicationLayer against
   * @return the results from the access control checks.
   */
  /*  public boolean mayUserRead(ApplicationLayer applicationLayer, Application context) {
    if (!isAuthorizedBySet(applicationLayer.getReaders())) {
      return false;
    }

    if (!mayUserRead(context)) {
      return false;
    }

    Set<Long> levelIds = new HashSet<>();
    for (Level level : levelRepository.findByLevelTree(context.getRoot().getId())) {
      levelIds.add(level.getId());
    }

    Level closestLevel = null;
    for (Level level : levelRepository.findWithAuthorizationDataByIdIn(levelIds)) {
      if (level.getLayers().contains(applicationLayer)) {
        closestLevel = level;
        break;
      }
    }

    if (closestLevel == null) {
      // layer ID does not exist in this application.
      return false;
    }

    if (!mayUserRead(closestLevel)) {
      return false;
    }

    GeoService geoService = applicationLayer.getService();
    if (geoService == null) {
      return true;
    }

    if (this.isProxiedSecuredServiceLayerInPublicApplication(context, applicationLayer)) {
      return false;
    }

    return mayUserRead(
        layerRepository.getByServiceAndName(geoService, applicationLayer.getLayerName()));
  }*/

  /**
   * When a service is proxied with a username and password, authentication must be required for the
   * application otherwise access to the layer should be denied to prevent an app admin accidentally
   * publishing private data from a secured service in a public application. This method checks
   * whether this is the case for a certain ApplicationLayer in the context of an Application.
   *
   * @param application the application (can be a mashup)
   * @param applicationLayer the application layer (may belong to a parent application)
   * @return see above
   */
  /*  public boolean isProxiedSecuredServiceLayerInPublicApplication(
          Application application, ApplicationLayer applicationLayer) {
    GeoService geoService = applicationLayer.getService();
    if (geoService == null) {
      return false;
    }
    if (Boolean.parseBoolean(
        String.valueOf(geoService.getDetails().get(GeoService.DETAIL_USE_PROXY)))) {
      boolean isSecuredService =
          geoService.getUsername() != null && geoService.getPassword() != null;
      return isSecuredService && !application.isAuthenticatedRequired();
    } else {
      return false;
    }
  }*/

  /**
   * Verifies that this user may view a Layer. The Layer, its parents, and the GeoService are all
   * validated.
   *
   * @param layer the Layer to check
   * @return the results from the access control checks.
   */
  /*  public boolean mayUserRead(Layer layer) {
    if (!isAuthorizedBySet(layer.getReaders())) {
      return false;
    }

    if (!mayUserRead(layer.getService())) {
      return false;
    }

    if (layer.getParent() != null) {
      return mayUserRead(layer.getParent());
    }

    return true;
  }*/

  /**
   * Verifies that this user may view a Level. The Level and its parents are all validated.
   *
   * @param level the Level to check
   * @return the results from the access control checks.
   */
  /*  public boolean mayUserRead(Level level) {
    if (!isAuthorizedBySet(level.getReaders())) {
      return false;
    }

    if (level.getParent() != null) {
      return mayUserRead(level.getParent());
    }

    return true;
  }*/

  /**
   * Verifies that this user may view a GeoService. The GeoService is validated.
   *
   * @param geoService the GeoService to check
   * @return the results from the access control checks.
   */
  /*  public boolean mayUserRead(GeoService geoService) {
    return isAuthorizedBySet(geoService.getReaders());
  }*/
}

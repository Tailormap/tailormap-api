/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.tailormap.api.persistence.Application;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.repository.FeatureSourceRepository;

@Component
public class EditUtil {
  private final FeatureSourceRepository featureSourceRepository;

  public EditUtil(FeatureSourceRepository featureSourceRepository) {
    this.featureSourceRepository = featureSourceRepository;
  }

  /**
   * Check if the current user is authenticated, throws exception if not. As we do not have editing authorisation any
   * known, authenticated user can edit.
   */
  public void checkEditAuthorisation() throws ResponseStatusException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
    if (!isAuthenticated) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not properly authenticated");
    }
  }

  /**
   * Get editable feature type, throws exception if not found or not editable. Will throw a
   * {@link ResponseStatusException} if the layer does not have an editable featuretype.
   *
   * @param application the application that has the editable layer
   * @param appTreeLayerNode the layer to edit
   * @param service the service that has the layer
   * @param layer the layer to edit
   * @return the editable feature type
   */
  public TMFeatureType getEditableFeatureType(
      Application application, AppTreeLayerNode appTreeLayerNode, GeoService service, GeoServiceLayer layer)
      throws ResponseStatusException {

    if (null == layer) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cannot find layer " + appTreeLayerNode);
    }

    AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);
    if (!Boolean.TRUE.equals(appLayerSettings.getEditable())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer is not editable");
    }

    TMFeatureType tmft = service.findFeatureTypeForLayer(layer, featureSourceRepository);
    if (null == tmft) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Layer does not have feature type");
    }
    if (!tmft.isWriteable()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Feature type is not writeable");
    }

    return tmft;
  }
}

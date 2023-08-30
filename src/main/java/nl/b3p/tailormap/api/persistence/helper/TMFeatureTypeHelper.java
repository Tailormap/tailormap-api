/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.helper;

import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppLayerSettings;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;

public class TMFeatureTypeHelper {
  public static boolean isEditable(
      Application application, AppTreeLayerNode appTreeLayerNode, TMFeatureType featureType) {
    if (featureType == null) {
      return false;
    }

    boolean editable = false;
    // Currently FeatureSourceHelper#loadCapabilities() sets editable to true when the datastore is
    // a JDBC DataStore (even when the database account can't write to the table, can't detect this
    // without trying). Other DataStore types we support (only WFS atm) we don't set as writeable

    // TODO: in the future, check for authorizations on editing. Currently you only need to be
    // logged in (the viewer frontend already checks this before showing editable layers so we don't
    // need to check for an authenticated user here).
    if (featureType.isWriteable()) {
      AppLayerSettings appLayerSettings = application.getAppLayerSettings(appTreeLayerNode);
      editable =
          Optional.ofNullable(appLayerSettings).map(AppLayerSettings::getEditable).orElse(false);
    }
    return editable;
  }
}

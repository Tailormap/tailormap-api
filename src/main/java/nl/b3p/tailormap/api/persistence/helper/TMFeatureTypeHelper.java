/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.helper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.Application;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AppLayerSettings;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.AttributeSettings;
import nl.b3p.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.apache.commons.lang3.tuple.Pair;

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

  /**
   * Return a map of attribute names (in order, using a LinkedHashMap implementation) to an
   * attribute descriptor and configured settings pair, taking into account the configured attribute
   * order and hidden attributes.
   *
   * @param featureType The feature type
   * @return A sorted map as described
   */
  public static Map<String, Pair<TMAttributeDescriptor, AttributeSettings>> getConfiguredAttributes(
      TMFeatureType featureType) {
    LinkedHashMap<String, TMAttributeDescriptor> originalAttributesOrder = new LinkedHashMap<>();
    for (TMAttributeDescriptor attributeDescriptor : featureType.getAttributes()) {
      originalAttributesOrder.put(attributeDescriptor.getName(), attributeDescriptor);
    }

    // Order of attributes taking into account hidden attributes and configured attribute order
    LinkedHashSet<String> finalAttributeOrder;

    if (featureType.getSettings().getAttributeOrder().isEmpty()) {
      // Use original feature type order
      finalAttributeOrder = new LinkedHashSet<>(originalAttributesOrder.keySet());
    } else {
      finalAttributeOrder = new LinkedHashSet<>(featureType.getSettings().getAttributeOrder());
      // Remove once ordered attributes which no longer exist in the feature type as saved in the
      // configuration database
      finalAttributeOrder.retainAll(originalAttributesOrder.keySet());
      // Add attributes not named in attributeOrder in feature type order (added to the feature type
      // after an admin saved/changed the ordering of attributes -- these attributes should not be
      // hidden).
      if (finalAttributeOrder.size() != originalAttributesOrder.size()) {
        finalAttributeOrder.addAll(originalAttributesOrder.keySet());
      }
    }

    featureType.getSettings().getHideAttributes().forEach(finalAttributeOrder::remove);

    Map<String, AttributeSettings> attributeSettings =
        featureType.getSettings().getAttributeSettings();
    LinkedHashMap<String, Pair<TMAttributeDescriptor, AttributeSettings>> result =
        new LinkedHashMap<>();
    for (String attribute : finalAttributeOrder) {
      AttributeSettings settings =
          Optional.ofNullable(attributeSettings.get(attribute)).orElseGet(AttributeSettings::new);
      TMAttributeDescriptor attributeDescriptor = originalAttributesOrder.get(attribute);
      result.put(attribute, Pair.of(attributeDescriptor, settings));
    }
    return result;
  }
}

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.helper;

import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AttributeSettings;
import nl.b3p.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class TMFeatureTypeHelper {
  /**
   * Return a map of attribute names (in order, using a LinkedHashMap implementation) to an attribute descriptor and configured settings pair, taking into account the configured attribute order and hidden attributes.
   * @param featureType The feature type
   * @return A sorted map as described
   */
  public static Map<String, Pair<TMAttributeDescriptor, AttributeSettings>> getConfiguredAttributes(TMFeatureType featureType) {
    LinkedHashMap<String,TMAttributeDescriptor> originalAttributesOrder = new LinkedHashMap<>();
    for (TMAttributeDescriptor attributeDescriptor: featureType.getAttributes()) {
      originalAttributesOrder.put(attributeDescriptor.getName(), attributeDescriptor);
    }

    // Order of attributes taking into account hidden attributes and configured attribute order
    LinkedHashSet<String> finalAttributeOrder;

    if (featureType.getSettings().getAttributeOrder().isEmpty()) {
      // Use original feature type order
      finalAttributeOrder = new LinkedHashSet<>(originalAttributesOrder.keySet());
    } else {
      finalAttributeOrder = new LinkedHashSet<>(featureType.getSettings().getAttributeOrder());
      // Remove once ordered attributes which no longer exist in the feature type as saved in the configuration database
      finalAttributeOrder.retainAll(originalAttributesOrder.keySet());
      // Add attributes not named in attributeOrder in feature type order (added to the feature type after an admin saved/changed the ordering of attributes -- these attributes should not be hidden).
      if (finalAttributeOrder.size() != originalAttributesOrder.size()) {
        finalAttributeOrder.addAll(originalAttributesOrder.keySet());
      }
    }

    featureType.getSettings().getHideAttributes().forEach(finalAttributeOrder::remove);

    Map<String, AttributeSettings> attributeSettings = featureType.getSettings().getAttributeSettings();
    LinkedHashMap<String, Pair<TMAttributeDescriptor, AttributeSettings>> result = new LinkedHashMap<>();
    for(String attribute: finalAttributeOrder) {
      AttributeSettings settings = Optional.ofNullable(attributeSettings.get(attribute)).orElseGet(AttributeSettings::new);
      TMAttributeDescriptor attributeDescriptor = originalAttributesOrder.get(attribute);
      result.put(attribute, Pair.of(attributeDescriptor, settings));
    }
    return result;
  }
}

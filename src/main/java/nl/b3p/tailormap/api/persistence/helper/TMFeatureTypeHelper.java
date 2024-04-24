/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.helper;

import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
      editable = Boolean.TRUE.equals(appLayerSettings.getEditable());
    }
    return editable;
  }

  public static Set<String> getHiddenAttributes(
      @NotNull TMFeatureType featureType, @NotNull AppLayerSettings appLayerSettings) {
    Set<String> hiddenAttributes = new HashSet<>();
    Optional.ofNullable(featureType.getSettings().getHideAttributes())
        .ifPresent(hiddenAttributes::addAll);
    Optional.ofNullable(appLayerSettings.getHideAttributes()).ifPresent(hiddenAttributes::addAll);
    return hiddenAttributes;
  }

  public static Set<String> getReadOnlyAttributes(
      @NotNull TMFeatureType featureType, @NotNull AppLayerSettings appLayerSettings) {
    Set<String> readOnlyAttributes = new HashSet<>();
    Optional.ofNullable(featureType.getSettings().getReadOnlyAttributes())
        .ifPresent(readOnlyAttributes::addAll);
    Optional.ofNullable(appLayerSettings.getReadOnlyAttributes())
        .ifPresent(readOnlyAttributes::addAll);
    return readOnlyAttributes;
  }

  /**
   * Return a map of attribute names (in order, using a LinkedHashMap implementation) to an
   * attribute descriptor and configured settings pair, taking into account the configured attribute
   * order and hidden attributes.
   *
   * @param featureType The feature type
   * @param appLayerSettings The app layer settings
   * @return A sorted map as described
   */
  public static Map<String, Pair<TMAttributeDescriptor, AttributeSettings>> getConfiguredAttributes(
      @NotNull TMFeatureType featureType, @NotNull AppLayerSettings appLayerSettings) {
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

    getHiddenAttributes(featureType, appLayerSettings).forEach(finalAttributeOrder::remove);

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

  /**
   * Get the non-hidden attribute descriptors for a feature type.
   *
   * @param featureType The feature type
   * @param appLayerSettings The app layer settings
   * @return Unordered set of attribute descriptors
   */
  public static Set<TMAttributeDescriptor> getNonHiddenAttributes(
      @NotNull TMFeatureType featureType, @NotNull AppLayerSettings appLayerSettings) {
    Set<String> hiddenAttributes = getHiddenAttributes(featureType, appLayerSettings);
    return featureType.getAttributes().stream()
        .filter(attributeDescriptor -> !hiddenAttributes.contains(attributeDescriptor.getName()))
        .collect(Collectors.toSet());
  }

  /**
   * Get the non-hidden attribute names for a feature type.
   *
   * @param featureType The feature type
   * @param appLayerSettings The app layer settings
   * @return Unordered set of attribute names
   */
  public static Set<String> getNonHiddenAttributeNames(
      @NotNull TMFeatureType featureType, @NotNull AppLayerSettings appLayerSettings) {
    return getNonHiddenAttributes(featureType, appLayerSettings).stream()
        .map(TMAttributeDescriptor::getName)
        .collect(Collectors.toSet());
  }
}

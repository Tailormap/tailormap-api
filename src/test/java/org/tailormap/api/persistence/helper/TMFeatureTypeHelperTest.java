/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.tailormap.api.persistence.TMFeatureType;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AttributeSettings;
import org.tailormap.api.persistence.json.FeatureTypeSettings;
import org.tailormap.api.persistence.json.TMAttributeDescriptor;

class TMFeatureTypeHelperTest {

  @Test
  void testGetConfiguredAttributes() {
    TMFeatureType ft = new TMFeatureType();
    final Function<String, TMAttributeDescriptor> att =
        (name) -> new TMAttributeDescriptor().name(name).comment("comment for " + name);
    ft.setAttributes(List.of(
        att.apply("a"),
        att.apply("b"),
        att.apply("c"),
        att.apply("d"),
        att.apply("e"),
        att.apply("f"),
        att.apply("m"),
        att.apply("n"),
        att.apply("o"),
        att.apply("q")));

    ft.setSettings(new FeatureTypeSettings()
        .putAttributeSettingsItem("d", new AttributeSettings().title("d title"))
        .addHideAttributesItem("b")
        .addHideAttributesItem("q")
        .addHideAttributesItem("o")
        .addAttributeOrderItem("c")
        .addAttributeOrderItem("d")
        .addAttributeOrderItem("e")
        .addAttributeOrderItem("o")
        .addAttributeOrderItem("a"));

    AppLayerSettings appLayerSettings = new AppLayerSettings();
    appLayerSettings.setHideAttributes(List.of("e", "f"));

    Map<String, Pair<TMAttributeDescriptor, AttributeSettings>> configuredAttributes =
        getConfiguredAttributes(ft, appLayerSettings);
    // Compare using Lists to explicitly check the ordering
    assertEquals(
        List.of(
            "c",
            "d",
            // "e" and "f" are hidden in app layer settings
            // "o" is hidden in feature type settings
            "a",
            // after ordered attributes, remaining attributes in original feature type order
            "m",
            "n"),
        new ArrayList<>(configuredAttributes.keySet()));

    assertEquals("d title", configuredAttributes.get("d").getRight().getTitle());

    assertEquals("comment for m", configuredAttributes.get("m").getLeft().getComment());
  }
}

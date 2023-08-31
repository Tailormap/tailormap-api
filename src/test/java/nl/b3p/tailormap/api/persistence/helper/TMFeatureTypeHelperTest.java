/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence.helper;

import static nl.b3p.tailormap.api.persistence.helper.TMFeatureTypeHelper.getConfiguredAttributes;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import nl.b3p.tailormap.api.persistence.TMFeatureType;
import nl.b3p.tailormap.api.persistence.json.AttributeSettings;
import nl.b3p.tailormap.api.persistence.json.FeatureTypeSettings;
import nl.b3p.tailormap.api.persistence.json.TMAttributeDescriptor;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

class TMFeatureTypeHelperTest {

  @Test
  void testGetConfiguredAttributes() {
    TMFeatureType ft = new TMFeatureType();
    final Function<String, TMAttributeDescriptor> att =
        (name) -> new TMAttributeDescriptor().name(name).comment("comment for " + name);
    ft.setAttributes(
        List.of(
            att.apply("a"),
            att.apply("b"),
            att.apply("c"),
            att.apply("d"),
            att.apply("m"),
            att.apply("n"),
            att.apply("o"),
            att.apply("q")));

    ft.setSettings(
        new FeatureTypeSettings()
            .putAttributeSettingsItem("d", new AttributeSettings().title("d title"))
            .addHideAttributesItem("b")
            .addHideAttributesItem("q")
            .addHideAttributesItem("o")
            .addAttributeOrderItem("c")
            .addAttributeOrderItem("o")
            .addAttributeOrderItem("a"));

    Map<String, Pair<TMAttributeDescriptor, AttributeSettings>> configuredAttributes =
        getConfiguredAttributes(ft);
    // Compare using Lists to explicitly check the ordering
    assertEquals(
        List.of(
            "c",
            // "o" is hidden
            "a",
            // after ordered attributes, remaining attributes in original feature type order
            "d",
            "m",
            "n"),
        new ArrayList<>(configuredAttributes.keySet()));

    assertEquals("d title", configuredAttributes.get("d").getRight().getTitle());

    assertEquals("comment for m", configuredAttributes.get("m").getLeft().getComment());
  }
}

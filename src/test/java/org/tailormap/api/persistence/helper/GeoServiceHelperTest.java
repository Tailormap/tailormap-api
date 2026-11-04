/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.tailormap.api.persistence.GeoService;
import org.tailormap.api.persistence.json.GeoServiceLayer;
import org.tailormap.api.persistence.json.WMSStyle;

class GeoServiceHelperTest {

  @Test
  void get_layer_legend_url_from_styles_does_not_modify_layer() {
    WMSStyle parentStyle = new WMSStyle().name("parent").legendUrl(URI.create("http://example.com/parent"));
    WMSStyle childStyle = new WMSStyle().name("child").legendUrl(URI.create("http://example.com/child"));

    GeoServiceLayer parent = new GeoServiceLayer()
        .id("0")
        .name("parent")
        .root(true)
        .styles(new ArrayList<>(List.of(parentStyle)))
        .children(new ArrayList<>(List.of("1")));
    // Child layer inherits the parent style, as in GeoServer capabilities
    GeoServiceLayer child = new GeoServiceLayer()
        .id("1")
        .name("child")
        .root(false)
        .styles(new ArrayList<>(List.of(parentStyle, childStyle)))
        .children(new ArrayList<>());

    GeoService service = new GeoService().setLayers(new ArrayList<>(List.of(parent, child)));

    int hashCodeBefore = child.hashCode();

    URI legendUrl = GeoServiceHelper.getLayerLegendUrlFromStyles(service, child);

    assertEquals(URI.create("http://example.com/child"), legendUrl);
    assertEquals(List.of(parentStyle, childStyle), child.getStyles(), "layer styles must not be modified");
    assertEquals(hashCodeBefore, child.hashCode(), "layer hashCode must not change");
  }
}

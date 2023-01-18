/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import nl.tailormap.viewer.config.app.Application;
import nl.tailormap.viewer.config.app.ApplicationLayer;
import nl.tailormap.viewer.config.services.FeatureSource;
import nl.tailormap.viewer.config.services.GeoService;
import nl.tailormap.viewer.config.services.Layer;
import nl.tailormap.viewer.config.services.SimpleFeatureType;
import nl.tailormap.viewer.config.services.WFSFeatureSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MicrometerHelper {
    public static Tags getTags(Object... objects) {
        List<Tag> tags = new ArrayList<>();
        for (Object o : objects) {
            if (o != null) {
                if (o instanceof Application) {
                    tags.add(Tag.of("appId", ((Application) o).getId() + ""));
                } else if (o instanceof ApplicationLayer) {
                    tags.add(Tag.of("appLayerId", ((ApplicationLayer) o).getId() + ""));
                } else if (o instanceof GeoService) {
                    tags.add(Tag.of("serviceId", ((GeoService) o).getId() + ""));
                } else if (o instanceof Layer) {
                    tags.add(Tag.of("serviceLayerId", ((Layer) o).getId() + ""));
                } else if (o instanceof SimpleFeatureType) {
                    SimpleFeatureType featureType = (SimpleFeatureType) o;
                    tags.add(Tag.of("simpleFeatureTypeId", featureType.getId() + ""));
                    FeatureSource featureSource = featureType.getFeatureSource();
                    if (featureSource instanceof WFSFeatureSource) {
                        WFSFeatureSource wfsFeatureSource = (WFSFeatureSource) featureSource;
                        tags.add(Tag.of("wfsUrl", wfsFeatureSource.getUrl()));
                        tags.add(Tag.of("wfsTypeName", featureType.getTypeName()));
                    }
                    // TODO: add more tags for other feature source types
                }
            }
        }
        return Tags.of(tags);
    }

    public static String tagsToString(Tags tags) {
        return tags.stream()
                .map(tag -> tag.getKey() + '=' + tag.getValue())
                .collect(Collectors.joining(","));
    }
}

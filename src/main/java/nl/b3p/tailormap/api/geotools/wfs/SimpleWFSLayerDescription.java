/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.geotools.wfs;

public class SimpleWFSLayerDescription {
    private final String wfsUrl;
    private final String[] typeNames;

    public SimpleWFSLayerDescription(String wfsUrl, String[] typeNames) {
        this.wfsUrl = wfsUrl;
        this.typeNames = typeNames;
    }

    public String getWfsUrl() {
        return wfsUrl;
    }

    public String[] getTypeNames() {
        return typeNames;
    }

    public String getFirstTypeName() {
        return typeNames[0];
    }
}

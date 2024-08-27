/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.solr;

import org.apache.solr.client.solrj.beans.Field;
import org.tailormap.api.util.Constants;

public class FeatureIndexingDocument implements Constants {
  @Field(value = ID)
  @SuppressWarnings("unused")
  private final String fid;

  @Field(value = SEARCH_LAYER)
  @SuppressWarnings("unused")
  private final Long searchLayer;

  @Field(value = INDEX_SEARCH_FIELD)
  @SuppressWarnings("unused")
  private String[] searchFields;

  @Field(value = INDEX_DISPLAY_FIELD)
  @SuppressWarnings("unused")
  private String[] displayFields;

  @Field(value = INDEX_GEOM_FIELD)
  @SuppressWarnings("unused")
  private String geometry;

  public FeatureIndexingDocument(String fid, Long searchLayerId) {
    this.fid = fid;
    this.searchLayer = searchLayerId;
  }

  public void setGeometry(String wktGeometry) {
    this.geometry = wktGeometry;
  }

  public void setSearchFields(String[] searchFields) {
    this.searchFields = searchFields;
  }

  public void setDisplayFields(String[] displayFields) {
    this.displayFields = displayFields;
  }
}

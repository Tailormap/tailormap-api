/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.solr;

import nl.b3p.tailormap.api.util.Constants;
import org.apache.solr.client.solrj.beans.Field;

public class FeatureDocument implements Constants {
  @Field(value = "id")
  private final String __fid;

  @Field(value = SEARCH_LAYER)
  private final String searchLayer;

  @Field(value = INDEX_SEARCH_FIELD)
  private String[] searchFields;

  @Field(value = INDEX_DISPLAY_FIELD)
  private String[] displayFields;

  @Field(value = INDEX_GEOM_FIELD)
  private String geometry;

  public FeatureDocument(String __fid, String searchLayerId) {
    this.__fid = __fid;
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

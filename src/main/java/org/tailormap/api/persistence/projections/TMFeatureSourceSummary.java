/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence.projections;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import org.springframework.data.rest.core.config.Projection;
import org.tailormap.api.persistence.TMFeatureSource;
import org.tailormap.api.persistence.json.JDBCConnectionProperties;
import org.tailormap.api.persistence.json.TMAttributeDescriptor;

@Projection(
    name = "summary",
    types = {TMFeatureSource.class})
public interface TMFeatureSourceSummary {

  Long getId();

  TMFeatureSource.Protocol getProtocol();

  @JsonIgnore
  JDBCConnectionProperties getJdbcConnection();

  default JDBCConnectionProperties.DbtypeEnum getDbType() {
    return getJdbcConnection() == null ? null : getJdbcConnection().getDbtype();
  }

  String getTitle();

  List<TMFeatureTypeSummary> getFeatureTypes();

  interface TMFeatureTypeSummary {
    Long getId();

    String getName();

    String getTitle();

    boolean isWriteable();

    @JsonIgnore
    List<TMAttributeDescriptor> getAttributes();

    default boolean getHasAttributes() {
      return !this.getAttributes().isEmpty();
    }
  }
}

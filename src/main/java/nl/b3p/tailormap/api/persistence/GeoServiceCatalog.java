/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import nl.b3p.tailormap.api.persistence.json.GeoServiceCatalogNode;
import org.hibernate.annotations.Type;

@Entity
public class GeoServiceCatalog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private List<GeoServiceCatalogNode> nodes;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public List<GeoServiceCatalogNode> getNodes() {
    return nodes;
  }

  public GeoServiceCatalog setNodes(List<GeoServiceCatalogNode> nodes) {
    this.nodes = nodes;
    return this;
  }
}

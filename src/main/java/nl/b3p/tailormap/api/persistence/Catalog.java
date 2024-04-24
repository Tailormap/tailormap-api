/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import nl.b3p.tailormap.api.persistence.json.CatalogNode;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;
import org.hibernate.annotations.Type;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class Catalog {
  public static final String MAIN = "main";
  @Id private String id;

  @Version private Long version;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private List<CatalogNode> nodes = new ArrayList<>();

  public String getId() {
    return id;
  }

  public Catalog setId(String id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public Catalog setVersion(Long version) {
    this.version = version;
    return this;
  }

  public List<CatalogNode> getNodes() {
    return nodes;
  }

  public Catalog setNodes(List<CatalogNode> nodes) {
    this.nodes = nodes;
    return this;
  }
}

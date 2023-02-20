/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.json.TMFeatureTypeInfo;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "feature_type")
public class TMFeatureType {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @NotNull private String name;

  @ManyToOne(cascade = CascadeType.PERSIST, optional = false)
  @JoinColumn(name = "feature_source")
  private TMFeatureSource featureSource;

  private String title;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private TMFeatureTypeInfo info;

  // Note: this will vanish when feature type disappears at the source, unless we move this to a
  // separate featureTypeSettings JSON property in TMFeatureSource
  @Column(columnDefinition = "text")
  private String comment;

  private String owner;

  private boolean writeable;

  private String defaultGeometryAttribute;

  // XXX: multiple primary keys?
  private String primaryKeyAttribute;

  @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
  @JoinTable(
      inverseJoinColumns = @JoinColumn(name = "attribute_descriptor"),
      name = "feature_type_attributes",
      joinColumns = @JoinColumn(name = "feature_type", referencedColumnName = "id"))
  @OrderColumn(name = "list_index")
  private List<TMAttributeDescriptor> attributes = new ArrayList<>();

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode settings;

  // <editor-fold desc="getters and setters">
  public Long getId() {
    return id;
  }

  public TMFeatureType setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public TMFeatureType setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getName() {
    return name;
  }

  public TMFeatureType setName(String type) {
    this.name = type;
    return this;
  }

  public TMFeatureSource getFeatureSource() {
    return featureSource;
  }

  public TMFeatureType setFeatureSource(TMFeatureSource featureSource) {
    this.featureSource = featureSource;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public TMFeatureType setTitle(String title) {
    this.title = title;
    return this;
  }

  public TMFeatureTypeInfo getInfo() {
    return info;
  }

  public TMFeatureType setInfo(TMFeatureTypeInfo info) {
    this.info = info;
    return this;
  }

  public String getComment() {
    return comment;
  }

  public TMFeatureType setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public String getOwner() {
    return owner;
  }

  public TMFeatureType setOwner(String owner) {
    this.owner = owner;
    return this;
  }

  public boolean isWriteable() {
    return writeable;
  }

  public TMFeatureType setWriteable(boolean writeable) {
    this.writeable = writeable;
    return this;
  }

  public String getDefaultGeometryAttribute() {
    return defaultGeometryAttribute;
  }

  public TMFeatureType setDefaultGeometryAttribute(String defaultGeometryAttribute) {
    this.defaultGeometryAttribute = defaultGeometryAttribute;
    return this;
  }

  public String getPrimaryKeyAttribute() {
    return primaryKeyAttribute;
  }

  public TMFeatureType setPrimaryKeyAttribute(String primaryKeyAttribute) {
    this.primaryKeyAttribute = primaryKeyAttribute;
    return this;
  }

  public List<TMAttributeDescriptor> getAttributes() {
    return attributes;
  }

  public TMFeatureType setAttributes(List<TMAttributeDescriptor> attributes) {
    this.attributes = attributes;
    return this;
  }

  public JsonNode getSettings() {
    return settings;
  }

  public TMFeatureType setSettings(JsonNode settings) {
    this.settings = settings;
    return this;
  }
  // </editor-fold>

  @PrePersist
  @PreUpdate
  public void checkDefaultGeometryAttribute() {
    if (defaultGeometryAttribute == null) {
      defaultGeometryAttribute =
          getAttributes().stream()
              .filter(TMAttributeDescriptor::isGeometry)
              .findFirst()
              .map(TMAttributeDescriptor::getName)
              .orElse(null);
    }
  }

  public Optional<TMAttributeDescriptor> getDefaultGeometryDescriptor() {
    if (defaultGeometryAttribute == null) {
      return Optional.empty();
    }
    return getAttributes().stream()
        .filter(a -> defaultGeometryAttribute.equals(a.getName()))
        .findFirst();
  }
}

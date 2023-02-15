/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "feature_type")
public class FeatureType {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @NotNull private String name;

  @ManyToOne(cascade = CascadeType.PERSIST, optional = false)
  @JoinColumn(name = "feature_source")
  private FeatureSource featureSource;

  private String description;

  // Note: this will vanish when feature type disappears at the source, unless we move this to a
  // separate featureTypeSettings JSON property in FeatureSource
  @Column(columnDefinition = "text")
  private String comment;

  private String owner;

  private boolean writeable;

  private String defaultGeometryAttribute;

  // XXX: multiple primary keys?
  private String primaryKeyAttribute;

  @OneToMany(cascade = CascadeType.ALL)
  @JoinTable(
      inverseJoinColumns = @JoinColumn(name = "attribute_descriptor"),
      name = "feature_type_attributes",
      joinColumns = @JoinColumn(name = "feature_type", referencedColumnName = "id"))
  @OrderColumn(name = "list_index")
  private List<AttributeDescriptor> attributes = new ArrayList<>();

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode settings;

  // <editor-fold desc="getters and setters">
  public Long getId() {
    return id;
  }

  public FeatureType setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FeatureType setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getName() {
    return name;
  }

  public FeatureType setName(String type) {
    this.name = type;
    return this;
  }

  public FeatureSource getFeatureSource() {
    return featureSource;
  }

  public FeatureType setFeatureSource(FeatureSource featureSource) {
    this.featureSource = featureSource;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public FeatureType setDescription(String description) {
    this.description = description;
    return this;
  }

  public String getComment() {
    return comment;
  }

  public FeatureType setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public String getOwner() {
    return owner;
  }

  public FeatureType setOwner(String owner) {
    this.owner = owner;
    return this;
  }

  public boolean isWriteable() {
    return writeable;
  }

  public FeatureType setWriteable(boolean writeable) {
    this.writeable = writeable;
    return this;
  }

  public String getDefaultGeometryAttribute() {
    return defaultGeometryAttribute;
  }

  public FeatureType setDefaultGeometryAttribute(String defaultGeometryAttribute) {
    this.defaultGeometryAttribute = defaultGeometryAttribute;
    return this;
  }

  public String getPrimaryKeyAttribute() {
    return primaryKeyAttribute;
  }

  public FeatureType setPrimaryKeyAttribute(String primaryKeyAttribute) {
    this.primaryKeyAttribute = primaryKeyAttribute;
    return this;
  }

  public List<AttributeDescriptor> getAttributes() {
    return attributes;
  }

  public FeatureType setAttributes(List<AttributeDescriptor> attributes) {
    this.attributes = attributes;
    return this;
  }

  public JsonNode getSettings() {
    return settings;
  }

  public FeatureType setSettings(JsonNode settings) {
    this.settings = settings;
    return this;
  }
  // </editor-fold>
}

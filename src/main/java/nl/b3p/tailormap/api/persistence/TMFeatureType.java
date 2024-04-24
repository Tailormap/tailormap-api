/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.helper.TMAttributeTypeHelper;
import nl.b3p.tailormap.api.persistence.json.FeatureTypeSettings;
import nl.b3p.tailormap.api.persistence.json.TMAttributeDescriptor;
import nl.b3p.tailormap.api.persistence.json.TMFeatureTypeInfo;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "feature_type")
@EntityListeners(EntityEventPublisher.class)
public class TMFeatureType {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @NotNull private String name;

  @ManyToOne(optional = false)
  @JoinColumn(name = "feature_source")
  private TMFeatureSource featureSource;

  private String title;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private TMFeatureTypeInfo info = new TMFeatureTypeInfo();

  // Note: this will vanish when feature type disappears at the source, unless we move this to a
  // separate featureTypeSettings JSON property in TMFeatureSource
  @Column(columnDefinition = "text")
  private String comment;

  private String owner;

  private boolean writeable;

  private String defaultGeometryAttribute;

  // XXX: multiple primary keys?
  private String primaryKeyAttribute;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private List<TMAttributeDescriptor> attributes = new ArrayList<>();

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private FeatureTypeSettings settings = new FeatureTypeSettings();

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

  public FeatureTypeSettings getSettings() {
    return settings;
  }

  public TMFeatureType setSettings(FeatureTypeSettings settings) {
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
              .filter(a -> TMAttributeTypeHelper.isGeometry(a.getType()))
              .findFirst()
              .map(TMAttributeDescriptor::getName)
              .orElse(null);
    }
  }

  public Optional<TMAttributeDescriptor> getDefaultGeometryDescriptor() {
    return getAttributeByName(defaultGeometryAttribute);
  }

  public Optional<TMAttributeDescriptor> getAttributeByName(String name) {
    if (name == null) {
      return Optional.empty();
    }
    return getAttributes().stream().filter(a -> name.equals(a.getName())).findFirst();
  }
}

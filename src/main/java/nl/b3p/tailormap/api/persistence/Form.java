/*
 * Copyright (C) 2024 B3Partners B.V.
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
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import nl.b3p.tailormap.api.persistence.json.FormField;
import nl.b3p.tailormap.api.persistence.json.FormOptions;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;
import org.hibernate.annotations.Type;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class Form {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @NotNull private String name;

  private Long featureSourceId;

  private String featureTypeName;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private FormOptions options = new FormOptions();

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private List<FormField> fields = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getFeatureSourceId() {
    return featureSourceId;
  }

  public void setFeatureSourceId(Long featureSourceId) {
    this.featureSourceId = featureSourceId;
  }

  public String getFeatureTypeName() {
    return featureTypeName;
  }

  public void setFeatureTypeName(String featureTypeName) {
    this.featureTypeName = featureTypeName;
  }

  public FormOptions getOptions() {
    return options;
  }

  public void setOptions(FormOptions options) {
    this.options = options;
  }

  public List<FormField> getFields() {
    return fields;
  }

  public void setFields(List<FormField> fields) {
    this.fields = fields;
  }
}

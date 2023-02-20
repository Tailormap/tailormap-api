/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import javax.persistence.AttributeConverter;
import javax.persistence.Column;
import javax.persistence.Converter;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.json.TMAttributeType;

@Entity
@Table(name = "attribute")
public class TMAttributeDescriptor {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String name;

  @Column(columnDefinition = "text")
  private String comment;

  // Converter to use lowercase enum value for aesthetic reasons
  @Converter(autoApply = true)
  public static class TMAttributeTypeConverter
      implements AttributeConverter<TMAttributeType, String> {
    @Override
    public String convertToDatabaseColumn(TMAttributeType attribute) {
      return attribute.getValue();
    }

    @Override
    public TMAttributeType convertToEntityAttribute(String dbData) {
      return TMAttributeType.fromValue(dbData);
    }
  }

  @NotNull private TMAttributeType type;

  private String unknownTypeClassName;

  private String description;

  public Long getId() {
    return id;
  }

  public TMAttributeDescriptor setId(Long id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public TMAttributeDescriptor setName(String name) {
    this.name = name;
    return this;
  }

  public String getComment() {
    return comment;
  }

  public TMAttributeDescriptor setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public TMAttributeType getType() {
    return type;
  }

  public TMAttributeDescriptor setType(TMAttributeType type) {
    this.type = type;
    return this;
  }

  public String getUnknownTypeClassName() {
    return unknownTypeClassName;
  }

  public TMAttributeDescriptor setUnknownTypeClassName(String unknownTypeClassName) {
    this.unknownTypeClassName = unknownTypeClassName;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public TMAttributeDescriptor setDescription(String description) {
    this.description = description;
    return this;
  }
}

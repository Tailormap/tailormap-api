/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.services;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.Basic;
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

@Entity
@Table(name = "feature_type")
public class FeatureType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;

    @ManyToOne(cascade = CascadeType.PERSIST, optional = false)
    @JoinColumn(name = "feature_source")
    private FeatureSource featureSource;

    @Basic(optional = false)
    private String typeName;

    private String description;

    private String comment;

    private String owner;

    @Column(columnDefinition = "jsonb")
    private Object additionalProperties;

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

    @Column(columnDefinition = "jsonb")
    private Object settings;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public FeatureSource getFeatureSource() {
        return featureSource;
    }

    public void setFeatureSource(FeatureSource featureSource) {
        this.featureSource = featureSource;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Object getAdditionalProperties() {
        return additionalProperties;
    }

    public void setAdditionalProperties(Object additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public boolean isWriteable() {
        return writeable;
    }

    public void setWriteable(boolean writeable) {
        this.writeable = writeable;
    }

    public String getDefaultGeometryAttribute() {
        return defaultGeometryAttribute;
    }

    public void setDefaultGeometryAttribute(String defaultGeometryAttribute) {
        this.defaultGeometryAttribute = defaultGeometryAttribute;
    }

    public String getPrimaryKeyAttribute() {
        return primaryKeyAttribute;
    }

    public void setPrimaryKeyAttribute(String primaryKeyAttribute) {
        this.primaryKeyAttribute = primaryKeyAttribute;
    }

    public List<AttributeDescriptor> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<AttributeDescriptor> attributes) {
        this.attributes = attributes;
    }

    public Object getSettings() {
        return settings;
    }

    public void setSettings(Object settings) {
        this.settings = settings;
    }
}

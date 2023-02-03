/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.viewer.model.AppResponse;
import nl.b3p.tailormap.api.viewer.model.AppStyling;
import nl.b3p.tailormap.api.viewer.model.Component;
import org.hibernate.annotations.Type;

import java.util.List;

@Entity
public class Application {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Basic(optional = false)
  private String name;

  private String title;

  @Column(columnDefinition = "text")
  private String adminComments;

  @Column(columnDefinition = "text")
  private String previewText;

  @Basic(optional = false)
  private String crs;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "crs", column = @Column(name = "start_crs")),
    @AttributeOverride(name = "minx", column = @Column(name = "start_minx")),
    @AttributeOverride(name = "maxx", column = @Column(name = "start_maxx")),
    @AttributeOverride(name = "miny", column = @Column(name = "start_miny")),
    @AttributeOverride(name = "maxy", column = @Column(name = "start_maxy"))
  })
  private BoundingBox startExtent;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "crs", column = @Column(name = "max_crs")),
    @AttributeOverride(name = "minx", column = @Column(name = "max_minx")),
    @AttributeOverride(name = "maxx", column = @Column(name = "max_maxx")),
    @AttributeOverride(name = "miny", column = @Column(name = "max_miny")),
    @AttributeOverride(name = "maxy", column = @Column(name = "max_maxy"))
  })
  private BoundingBox maxExtent;

  private boolean authenticatedRequired;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private AppContent contentRoot;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode layerSettings;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private List<Component> components;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private AppStyling styling;

  // <editor-fold desc="getters and setters">
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAdminComments() {
    return adminComments;
  }

  public void setAdminComments(String adminComments) {
    this.adminComments = adminComments;
  }

  public String getPreviewText() {
    return previewText;
  }

  public void setPreviewText(String previewText) {
    this.previewText = previewText;
  }

  public String getCrs() {
    return crs;
  }

  public void setCrs(String crs) {
    this.crs = crs;
  }

  public BoundingBox getStartExtent() {
    return startExtent;
  }

  public void setStartExtent(BoundingBox startExtent) {
    this.startExtent = startExtent;
  }

  public BoundingBox getMaxExtent() {
    return maxExtent;
  }

  public void setMaxExtent(BoundingBox maxExtent) {
    this.maxExtent = maxExtent;
  }

  public boolean isAuthenticatedRequired() {
    return authenticatedRequired;
  }

  public void setAuthenticatedRequired(boolean authenticatedRequired) {
    this.authenticatedRequired = authenticatedRequired;
  }

  public AppContent getContentRoot() {
    return contentRoot;
  }

  public void setContentRoot(AppContent contentRoot) {
    this.contentRoot = contentRoot;
  }

  public JsonNode getLayerSettings() {
    return layerSettings;
  }

  public void setLayerSettings(JsonNode layerSettings) {
    this.layerSettings = layerSettings;
  }

  public List<Component> getComponents() {
    return components;
  }

  public void setComponents(List<Component> components) {
    this.components = components;
  }
  public AppStyling getStyling() {
    return styling;
  }

  public void setStyling(AppStyling styling) {
    this.styling = styling;
  }
  // </editor-fold>

  public AppResponse toAppResponse() {
    return new AppResponse().id(id).name(name).title(title).styling(styling).components(components);
  }
}

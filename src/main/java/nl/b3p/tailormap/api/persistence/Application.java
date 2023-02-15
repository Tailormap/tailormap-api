/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.lang.invoke.MethodHandles;
import java.util.List;
import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.viewer.model.AppResponse;
import nl.b3p.tailormap.api.viewer.model.AppStyling;
import nl.b3p.tailormap.api.viewer.model.Bounds;
import nl.b3p.tailormap.api.viewer.model.Component;
import org.geotools.referencing.CRS;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
public class Application {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @NotNull private String name;

  private String title;

  @Column(columnDefinition = "text")
  private String adminComments;

  @Column(columnDefinition = "text")
  private String previewText;

  @NotNull private String crs;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "minx", column = @Column(name = "initial_minx")),
    @AttributeOverride(name = "maxx", column = @Column(name = "initial_maxx")),
    @AttributeOverride(name = "miny", column = @Column(name = "initial_miny")),
    @AttributeOverride(name = "maxy", column = @Column(name = "initial_maxy"))
  })
  private Bounds initialExtent;

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "minx", column = @Column(name = "max_minx")),
    @AttributeOverride(name = "maxx", column = @Column(name = "max_maxx")),
    @AttributeOverride(name = "miny", column = @Column(name = "max_miny")),
    @AttributeOverride(name = "maxy", column = @Column(name = "max_maxy"))
  })
  private Bounds maxExtent;

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

  public Application setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public Application setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getName() {
    return name;
  }

  public Application setName(String name) {
    this.name = name;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public Application setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getAdminComments() {
    return adminComments;
  }

  public Application setAdminComments(String adminComments) {
    this.adminComments = adminComments;
    return this;
  }

  public String getPreviewText() {
    return previewText;
  }

  public Application setPreviewText(String previewText) {
    this.previewText = previewText;
    return this;
  }

  public String getCrs() {
    return crs;
  }

  public Application setCrs(String crs) {
    this.crs = crs;
    return this;
  }

  public Bounds getInitialExtent() {
    return initialExtent;
  }

  public Application setInitialExtent(Bounds initialExtent) {
    this.initialExtent = initialExtent;
    return this;
  }

  public Bounds getMaxExtent() {
    return maxExtent;
  }

  public Application setMaxExtent(Bounds maxExtent) {
    this.maxExtent = maxExtent;
    return this;
  }

  public boolean isAuthenticatedRequired() {
    return authenticatedRequired;
  }

  public Application setAuthenticatedRequired(boolean authenticatedRequired) {
    this.authenticatedRequired = authenticatedRequired;
    return this;
  }

  public AppContent getContentRoot() {
    return contentRoot;
  }

  public Application setContentRoot(AppContent contentRoot) {
    this.contentRoot = contentRoot;
    return this;
  }

  public JsonNode getLayerSettings() {
    return layerSettings;
  }

  public Application setLayerSettings(JsonNode layerSettings) {
    this.layerSettings = layerSettings;
    return this;
  }

  public List<Component> getComponents() {
    return components;
  }

  public Application setComponents(List<Component> components) {
    this.components = components;
    return this;
  }

  public AppStyling getStyling() {
    return styling;
  }

  public Application setStyling(AppStyling styling) {
    this.styling = styling;
    return this;
  }

  // </editor-fold>

  public AppResponse toAppResponse() {
    return new AppResponse().id(id).name(name).title(title).styling(styling).components(components);
  }

  /**
   * Return a GeoTools CoordinateReferenceSystem from this entities' CRS code or null if there is an
   * error decoding it, which will be logged (only with stacktrace if loglevel is DEBUG).
   *
   * @return CoordinateReferenceSystem
   */
  @JsonIgnore
  public org.opengis.referencing.crs.CoordinateReferenceSystem
      getGeoToolsCoordinateReferenceSystem() {
    org.opengis.referencing.crs.CoordinateReferenceSystem gtCrs = null;
    try {
      if (getCrs() != null) {
        gtCrs = CRS.decode(getCrs());
      }
    } catch (Exception e) {
      String message =
          String.format(
              "Application %d: error decoding CRS from code \"%s\": %s: %s",
              getId(), getCrs(), e.getClass(), e.getMessage());
      if (logger.isDebugEnabled()) {
        logger.error(message, e);
      } else {
        logger.error(message);
      }
    }
    return gtCrs;
  }
}

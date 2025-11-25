/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.geotools.referencing.CRS;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.tailormap.api.persistence.json.AppContent;
import org.tailormap.api.persistence.json.AppLayerSettings;
import org.tailormap.api.persistence.json.AppSettings;
import org.tailormap.api.persistence.json.AppTreeLayerNode;
import org.tailormap.api.persistence.json.AuthorizationRule;
import org.tailormap.api.persistence.json.Bounds;
import org.tailormap.api.persistence.listener.EntityEventPublisher;
import org.tailormap.api.viewer.model.AppStyling;
import org.tailormap.api.viewer.model.Component;

@Audited
@Entity
@EntityListeners({EntityEventPublisher.class, AuditingEntityListener.class})
public class Application extends AuditMetadata {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @Column(unique = true)
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

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  @NotNull private AppContent contentRoot = new AppContent();

  @JsonIgnore
  private transient AppContent oldContentRoot;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  @NotNull private AppSettings settings = new AppSettings();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  @NotNull private List<Component> components = new ArrayList<>();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  @NotNull private AppStyling styling = new AppStyling();

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  @NotNull private List<AuthorizationRule> authorizationRules = new ArrayList<>();

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

  public AppContent getContentRoot() {
    return contentRoot;
  }

  public Application setContentRoot(AppContent contentRoot) {
    this.oldContentRoot = this.contentRoot;
    this.contentRoot = contentRoot;
    return this;
  }
  /**
   * This method is used to get the old content root before it is updated. It is used in the ApplicationEventHandler
   * to compare the old and new content roots, more specifically to get the old AppTreeLayerNode nodes.
   *
   * @see #getAllOldAppTreeLayerNode()
   */
  public AppContent getOldContentRoot() {
    return this.oldContentRoot;
  }

  public AppSettings getSettings() {
    return settings;
  }

  public Application setSettings(AppSettings layerSettings) {
    this.settings = layerSettings;
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

  public List<AuthorizationRule> getAuthorizationRules() {
    return authorizationRules;
  }

  public Application setAuthorizationRules(List<AuthorizationRule> authorizationRules) {
    this.authorizationRules = authorizationRules;
    return this;
  }

  // </editor-fold>
  @JsonIgnore
  public Stream<AppTreeLayerNode> getAllOldAppTreeLayerNode() {
    return this.getAppTreeLayerNodeStream(this.getOldContentRoot());
  }

  @JsonIgnore
  public Stream<AppTreeLayerNode> getAllAppTreeLayerNode() {
    return this.getAppTreeLayerNodeStream(this.getContentRoot());
  }

  @JsonIgnore
  private Stream<AppTreeLayerNode> getAppTreeLayerNodeStream(AppContent contentRoot) {
    if (contentRoot == null) {
      return Stream.empty();
    }
    Stream<AppTreeLayerNode> baseLayers = Stream.empty();
    if (contentRoot.getBaseLayerNodes() != null) {
      baseLayers = contentRoot.getBaseLayerNodes().stream()
          .filter(n -> "AppTreeLayerNode".equals(n.getObjectType()))
          .map(n -> (AppTreeLayerNode) n);
    }
    Stream<AppTreeLayerNode> layers = Stream.empty();
    if (contentRoot.getLayerNodes() != null) {
      layers = contentRoot.getLayerNodes().stream()
          .filter(n -> "AppTreeLayerNode".equals(n.getObjectType()))
          .map(n -> (AppTreeLayerNode) n);
    }
    return Stream.concat(baseLayers, layers);
  }

  /**
   * Return a GeoTools CoordinateReferenceSystem from this entities' CRS code or null if there is an error decoding
   * it, which will be logged (only with stacktrace if loglevel is DEBUG).
   *
   * @return CoordinateReferenceSystem
   */
  @JsonIgnore
  public org.geotools.api.referencing.crs.CoordinateReferenceSystem getGeoToolsCoordinateReferenceSystem() {
    org.geotools.api.referencing.crs.CoordinateReferenceSystem gtCrs = null;
    try {
      if (getCrs() != null) {
        gtCrs = CRS.decode(getCrs());
      }
    } catch (Exception e) {
      String message = "Application %d: error decoding CRS from code \"%s\": %s: %s"
          .formatted(getId(), getCrs(), e.getClass(), e.getMessage());
      if (logger.isDebugEnabled()) {
        logger.error(message, e);
      } else {
        logger.error(message);
      }
    }
    return gtCrs;
  }

  @NotNull public AppLayerSettings getAppLayerSettings(@NotNull AppTreeLayerNode node) {
    return getAppLayerSettings(node.getId());
  }

  @NotNull public AppLayerSettings getAppLayerSettings(@NotNull String appLayerId) {
    return Optional.ofNullable(getSettings())
        .map(AppSettings::getLayerSettings)
        .map(layerSettingsMap -> layerSettingsMap.get(appLayerId))
        .orElseGet(AppLayerSettings::new);
  }
}

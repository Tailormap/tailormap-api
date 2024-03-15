/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import static java.util.Objects.requireNonNullElse;

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
import nl.b3p.tailormap.api.persistence.json.AppContent;
import nl.b3p.tailormap.api.persistence.json.AppI18nSettings;
import nl.b3p.tailormap.api.persistence.json.AppLayerSettings;
import nl.b3p.tailormap.api.persistence.json.AppSettings;
import nl.b3p.tailormap.api.persistence.json.AppTreeLayerNode;
import nl.b3p.tailormap.api.persistence.json.AppUiSettings;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRule;
import nl.b3p.tailormap.api.persistence.json.Bounds;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;
import nl.b3p.tailormap.api.viewer.model.AppStyling;
import nl.b3p.tailormap.api.viewer.model.Component;
import nl.b3p.tailormap.api.viewer.model.ViewerResponse;
import org.geotools.referencing.CRS;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class Application {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @Column(unique = true)
  @NotNull
  private String name;

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

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private AppContent contentRoot = new AppContent();

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private AppSettings settings = new AppSettings();

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private List<Component> components = new ArrayList<>();

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private AppStyling styling = new AppStyling();

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private List<AuthorizationRule> authorizationRules = new ArrayList<>();

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
    this.contentRoot = contentRoot;
    return this;
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
  public Stream<AppTreeLayerNode> getAllAppTreeLayerNode() {
    if (this.getContentRoot() == null) {
      return Stream.empty();
    }
    Stream<AppTreeLayerNode> baseLayers = Stream.empty();
    if (this.getContentRoot().getBaseLayerNodes() != null) {
      baseLayers =
          this.getContentRoot().getBaseLayerNodes().stream()
              .filter(n -> "AppTreeLayerNode".equals(n.getObjectType()))
              .map(n -> (AppTreeLayerNode) n);
    }
    Stream<AppTreeLayerNode> layers = Stream.empty();
    if (this.getContentRoot().getLayerNodes() != null) {
      layers =
          this.getContentRoot().getLayerNodes().stream()
              .filter(n -> "AppTreeLayerNode".equals(n.getObjectType()))
              .map(n -> (AppTreeLayerNode) n);
    }
    return Stream.concat(baseLayers, layers);
  }

  /**
   * Return a GeoTools CoordinateReferenceSystem from this entities' CRS code or null if there is an
   * error decoding it, which will be logged (only with stacktrace if loglevel is DEBUG).
   *
   * @return CoordinateReferenceSystem
   */
  @JsonIgnore
  public org.geotools.api.referencing.crs.CoordinateReferenceSystem
      getGeoToolsCoordinateReferenceSystem() {
    org.geotools.api.referencing.crs.CoordinateReferenceSystem gtCrs = null;
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

  @JsonIgnore
  public ViewerResponse getViewerResponse() {
    return new ViewerResponse()
        .kind(ViewerResponse.KindEnum.APP)
        .name(getName())
        .title(getTitle())
        .styling(styling)
        .components(components)
        .i18nSettings(
            requireNonNullElse(
                settings.getI18nSettings(), new AppI18nSettings().hideLanguageSwitcher(false)))
        .uiSettings(
            requireNonNullElse(
                settings.getUiSettings(), new AppUiSettings().hideLoginButton(false)))
        .projections(List.of(getCrs()));
  }

  @NotNull
  public AppLayerSettings getAppLayerSettings(@NotNull AppTreeLayerNode node) {
    return Optional.ofNullable(getSettings())
        .map(AppSettings::getLayerSettings)
        .map(layerSettingsMap -> layerSettingsMap.get(node.getId()))
        .orElseGet(AppLayerSettings::new);
  }
}

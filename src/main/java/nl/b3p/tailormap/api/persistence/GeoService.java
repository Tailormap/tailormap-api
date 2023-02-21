/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import nl.b3p.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol;
import nl.b3p.tailormap.api.persistence.json.GeoServiceSettings;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import nl.b3p.tailormap.api.persistence.json.TMServiceCaps;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.viewer.model.Service;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Entity
public class GeoService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @Column(columnDefinition = "text")
  private String notes;

  @NotNull
  @Enumerated(EnumType.STRING)
  private GeoServiceProtocol protocol;

  /**
   * The URL from which the capabilities of this service can be loaded and the URL to use for the
   * service, except when the advertisedUrl should be used by explicit user request. TODO: never use
   * URL in capabilities? TODO: explicitly specify relative URLs can be used? Or even if it should
   * be automatically converted to a relative URL if the hostname/port matches our URL? TODO: what
   * to do with parameters such as VERSION in the URL?
   */
  @NotNull
  @Column(length = 2048)
  private String url;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private ServiceAuthentication authentication;

  /**
   * Original capabilities as received from the service. This can be used for capability information
   * not already parsed in this entity, such as tiling information.
   */
  @Basic(fetch = FetchType.LAZY)
  @JsonIgnore
  private byte[] capabilities;

  /**
   * Content type of capabilities. "application/xml" for WMS/WMTS and "application/json" for REST
   * services.
   */
  private String capabilitiesContentType;

  /** The instant the capabilities where last successfully fetched and parsed. */
  private Instant capabilitiesFetched;

  /** Title loaded from capabilities or as modified by user for display. */
  @NotNull
  @Column(length = 2048)
  private String title;

  /**
   * A service may advertise a URL in its capabilities which does not actually work, for example if
   * the service is behind a proxy. Usually this shouldn't be used.
   */
  @Column(length = 2048)
  private String advertisedUrl;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private TMServiceCaps serviceCapabilities;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private List<GeoServiceLayer> layers = new ArrayList<>();

  /**
   * Settings relevant for Tailormap use cases, such as configuring the specific server type for
   * vendor-specific capabilities etc.
   */
  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private GeoServiceSettings settings = new GeoServiceSettings();

  // <editor-fold desc="getters and setters">
  public Long getId() {
    return id;
  }

  public GeoService setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public GeoService setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getNotes() {
    return notes;
  }

  public GeoService setNotes(String adminComments) {
    this.notes = adminComments;
    return this;
  }

  public GeoServiceProtocol getProtocol() {
    return protocol;
  }

  public GeoService setProtocol(GeoServiceProtocol protocol) {
    this.protocol = protocol;
    return this;
  }

  public String getUrl() {
    return url;
  }

  public GeoService setUrl(String url) {
    this.url = url;
    return this;
  }

  public ServiceAuthentication getAuthentication() {
    return authentication;
  }

  public GeoService setAuthentication(ServiceAuthentication authentication) {
    this.authentication = authentication;
    return this;
  }

  public byte[] getCapabilities() {
    return capabilities;
  }

  public GeoService setCapabilities(byte[] capabilities) {
    this.capabilities = capabilities;
    return this;
  }

  public String getCapabilitiesContentType() {
    return capabilitiesContentType;
  }

  public GeoService setCapabilitiesContentType(String capabilitiesContentType) {
    this.capabilitiesContentType = capabilitiesContentType;
    return this;
  }

  public Instant getCapabilitiesFetched() {
    return capabilitiesFetched;
  }

  public GeoService setCapabilitiesFetched(Instant capabilitiesFetched) {
    this.capabilitiesFetched = capabilitiesFetched;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public GeoService setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getAdvertisedUrl() {
    return advertisedUrl;
  }

  public GeoService setAdvertisedUrl(String advertisedUrl) {
    this.advertisedUrl = advertisedUrl;
    return this;
  }

  public TMServiceCaps getServiceCapabilities() {
    return serviceCapabilities;
  }

  public GeoService setServiceCapabilities(TMServiceCaps serviceCapabilities) {
    this.serviceCapabilities = serviceCapabilities;
    return this;
  }

  public List<GeoServiceLayer> getLayers() {
    return layers;
  }

  public GeoService setLayers(List<GeoServiceLayer> layers) {
    this.layers = layers;
    return this;
  }

  public GeoServiceSettings getSettings() {
    return settings;
  }

  public GeoService setSettings(GeoServiceSettings settings) {
    this.settings = settings;
    return this;
  }
  // </editor-fold>

  public Service toJsonPojo(GeoServiceHelper geoServiceHelper) {
    Service.ServerTypeEnum serverTypeEnum;
    if (settings.getServerType() == GeoServiceSettings.ServerTypeEnum.AUTO) {
      serverTypeEnum = geoServiceHelper.guessServerTypeFromUrl(getUrl());
    } else {
      serverTypeEnum = Service.ServerTypeEnum.fromValue(settings.getServerType().getValue());
    }

    Service s =
        new Service()
            .id(this.id)
            .title(this.title)
            .url(this.url)
            .protocol(this.protocol)
            .serverType(serverTypeEnum);

    if (this.protocol == GeoServiceProtocol.WMTS) {
      // Frontend requires WMTS capabilities to parse TilingMatrix, but WMS capabilities aren't used
      // XXX UTF-8 only, maybe use base64
      s.capabilities(new String(getCapabilities(), StandardCharsets.UTF_8));
    }

    return s;
  }

  public GeoServiceLayer findLayer(String name) {
    return getLayers().stream().filter(sl -> name.equals(sl.getName())).findFirst().orElse(null);
  }

  public GeoServiceLayerSettings getLayerSettings(String layerName) {
    return Optional.ofNullable(getSettings().getLayerSettings())
        .map(m -> m.get(layerName))
        .orElse(null);
  }

  public String getTitleWithDefaults(String layerName) {
    // First use title in layer settings
    String title =
        Optional.ofNullable(getLayerSettings(layerName))
            .map(GeoServiceLayerSettings::getTitle)
            .orElse(null);

    // If not set, title from capabilities
    if (title == null) {
      title = Optional.ofNullable(findLayer(layerName)).map(GeoServiceLayer::getTitle).orElse(null);
    }

    // Do not get title from default layer settings (a default wouldn't make sense)

    // If still not set, use layer name as title
    if (title == null) {
      title = layerName;
    }

    return title;
  }

  public TMFeatureType findFeatureTypeForLayer(
      GeoServiceLayer layer, FeatureSourceRepository featureSourceRepository) {

    GeoServiceDefaultLayerSettings defaultLayerSettings = getSettings().getDefaultLayerSettings();
    GeoServiceLayerSettings layerSettings = getLayerSettings(layer.getName());

    Long featureSourceId = null;
    String featureTypeName;

    if (layerSettings != null && layerSettings.getFeatureType() != null) {
      featureTypeName =
          Optional.ofNullable(layerSettings.getFeatureType().getFeatureTypeName())
              .orElse(layer.getName());
      featureSourceId = layerSettings.getFeatureType().getFeatureSourceId();
    } else {
      featureTypeName = layer.getName();
    }

    if (featureSourceId == null
        && defaultLayerSettings != null
        && defaultLayerSettings.getFeatureType() != null) {
      featureSourceId = defaultLayerSettings.getFeatureType().getFeatureSourceId();
    }

    if (featureTypeName == null) {
      return null;
    }

    TMFeatureSource tmfs;
    if (featureSourceId == null) {
      tmfs = featureSourceRepository.findByLinkedServiceId(getId()).orElse(null);
    } else {
      tmfs = featureSourceRepository.findById(featureSourceId).orElse(null);
    }

    if (tmfs == null) {
      return null;
    }
    TMFeatureType tmft =
        tmfs.getFeatureTypes().stream()
            .filter(ft -> featureTypeName.equals(ft.getName()))
            .findFirst()
            .orElse(null);

    if (tmft == null) {
      String[] split = featureTypeName.split(":", 2);
      if (split.length == 2) {
        String shortFeatureTypeName = split[1];
        tmft =
            tmfs.getFeatureTypes().stream()
                .filter(ft -> shortFeatureTypeName.equals(ft.getName()))
                .findFirst()
                .orElse(null);
        if (tmft != null) {
          logger.debug(
              "Did not find feature type with full name \"{}\", using \"{}\" of feature source {}",
              featureTypeName,
              shortFeatureTypeName,
              tmfs);
        }
      }
    }
    return tmft;
  }
}

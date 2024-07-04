/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.hypersistence.tsid.TSID;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import nl.b3p.tailormap.api.persistence.json.AuthorizationRule;
import nl.b3p.tailormap.api.persistence.json.GeoServiceDefaultLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceProtocol;
import nl.b3p.tailormap.api.persistence.json.GeoServiceSettings;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import nl.b3p.tailormap.api.persistence.json.TMServiceCaps;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;
import nl.b3p.tailormap.api.repository.FeatureSourceRepository;
import nl.b3p.tailormap.api.util.TMStringUtils;
import nl.b3p.tailormap.api.viewer.model.Service;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class GeoService {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final List<String> REMOVE_PARAMS = List.of("REQUEST");

  @Id private String id;

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

  @Transient private boolean refreshCapabilities;

  /**
   * Non-null when authentication is required for this service. Currently, the only authentication
   * method is password (HTTP Basic).
   */
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
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

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private TMServiceCaps serviceCapabilities;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private List<AuthorizationRule> authorizationRules = new ArrayList<>();

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private List<GeoServiceLayer> layers = new ArrayList<>();

  private boolean published;

  /**
   * Settings relevant for Tailormap use cases, such as configuring the specific server type for
   * vendor-specific capabilities etc.
   */
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @NotNull
  private GeoServiceSettings settings = new GeoServiceSettings();

  // <editor-fold desc="getters and setters">
  public String getId() {
    return id;
  }

  public GeoService setId(String id) {
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

  /** Sets the url after sanitising (removing unwanted parameters). */
  public GeoService setUrl(String url) {
    this.url = sanitiseUrl(url);
    return this;
  }

  public boolean isRefreshCapabilities() {
    return refreshCapabilities;
  }

  public void setRefreshCapabilities(boolean refreshCapabilities) {
    this.refreshCapabilities = refreshCapabilities;
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

  public List<AuthorizationRule> getAuthorizationRules() {
    return authorizationRules;
  }

  public GeoService setAuthorizationRules(List<AuthorizationRule> authorizationRules) {
    this.authorizationRules = authorizationRules;
    return this;
  }

  public List<GeoServiceLayer> getLayers() {
    return layers;
  }

  public GeoService setLayers(List<GeoServiceLayer> layers) {
    this.layers = layers;
    return this;
  }

  public boolean isPublished() {
    return published;
  }

  public GeoService setPublished(boolean published) {
    this.published = published;
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

  @PrePersist
  public void assignId() {
    if (StringUtils.isBlank(getId())) {
      // We kind of misuse TSIDs here, because we store it as a string. This is because the id
      // string can also be manually assigned. There won't be huge numbers of GeoServices, so it's
      // more of a convenient way to generate an ID that isn't a huge UUID string.
      setId(TSID.fast().toString());
    }
  }

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
            .url(Boolean.TRUE.equals(this.getSettings().getUseProxy()) ? null : this.url)
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
    return getSettings().getLayerSettings().get(layerName);
  }

  @NonNull
  public String getTitleWithSettingsOverrides(String layerName) {
    // First use title in layer settings
    String title =
        Optional.ofNullable(getLayerSettings(layerName))
            .map(GeoServiceLayerSettings::getTitle)
            .map(TMStringUtils::nullIfEmpty)
            .orElse(null);

    // If not set, title from capabilities
    if (title == null) {
      title =
          Optional.ofNullable(findLayer(layerName))
              .map(GeoServiceLayer::getTitle)
              .map(TMStringUtils::nullIfEmpty)
              .orElse(null);
    }

    // Do not get title from default layer settings (a default title wouldn't make sense)

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

    TMFeatureSource tmfs = null;
    TMFeatureType tmft = null;

    if (featureSourceId == null) {
      List<TMFeatureSource> linkedSources = featureSourceRepository.findByLinkedServiceId(getId());
      for (TMFeatureSource linkedFs : linkedSources) {
        tmft = linkedFs.findFeatureTypeByName(featureTypeName);
        if (tmft != null) {
          tmfs = linkedFs;
          break;
        }
      }
    } else {
      tmfs = featureSourceRepository.findById(featureSourceId).orElse(null);
      if (tmfs != null) {
        tmft = tmfs.findFeatureTypeByName(featureTypeName);
      }
    }

    if (tmfs == null) {
      return null;
    }

    if (tmft == null) {
      String[] split = featureTypeName.split(":", 2);
      if (split.length == 2) {
        String shortFeatureTypeName = split[1];
        tmft = tmfs.findFeatureTypeByName(shortFeatureTypeName);
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

  /**
   * Remove all parameters from the URL that are listed in {@link #REMOVE_PARAMS}.
   *
   * @param url URL to sanitise
   * @return sanitised URL
   */
  private String sanitiseUrl(String url) {
    if (url != null && url.contains("?")) {
      MultiValueMap<String, String> sanitisedParams = new LinkedMultiValueMap<>();
      UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(url);
      MultiValueMap<String, String> /* unmodifiable */ requestParams = uri.build().getQueryParams();
      for (Map.Entry<String, List<String>> param : requestParams.entrySet()) {
        if (!REMOVE_PARAMS.contains(param.getKey().toUpperCase(Locale.ROOT))) {
          sanitisedParams.put(param.getKey(), param.getValue());
        }
      }

      url = uri.replaceQueryParams(sanitisedParams).build().toUriString();
      if (url.endsWith("?")) {
        url = url.substring(0, url.length() - 1);
      }
    }
    return url;
  }
}

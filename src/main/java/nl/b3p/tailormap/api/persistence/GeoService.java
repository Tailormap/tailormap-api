/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.helper.GeoServiceHelper;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayerSettings;
import nl.b3p.tailormap.api.persistence.json.GeoServiceSettings;
import nl.b3p.tailormap.api.persistence.json.ServiceCaps;
import nl.b3p.tailormap.api.viewer.model.Service;
import org.hibernate.annotations.Type;

@Entity
public class GeoService {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(columnDefinition = "text")
  private String adminComments;

  @Basic @NotNull private String protocol;

  /**
   * The URL from which the capabilities of this service can be loaded and the URL to use for the
   * service, except when the advertisedUrl should be used by explicit user request. TODO: never use
   * URL in capabilities? TODO: explicitly specify relative URLs can be used? Or even if it should
   * be automatically converted to a relative URL if the hostname/port matches our URL? TODO: what
   * to do with parameters such as VERSION in the URL?
   */
  @Basic
  @NotNull
  @Column(length = 2048)
  private String url;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode authentication;

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
  @Basic
  @NotNull
  @Column(length = 2048)
  private String title;

  /**
   * A service may advertise a URL in its capabilities which does not actually work, for example if
   * the service is behind a proxy. Usually this shouldn't be used.
   */
  @Column(length = 2048)
  @Basic
  private String advertisedUrl;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private ServiceCaps serviceCapabilities;

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

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode tileServiceInfo;

  // TODO: Zit nu in json serviceInfo, handig? Native query nodig voor JSON component query.
  //    @ElementCollection
  //    @CollectionTable(joinColumns = @JoinColumn(name = "geo_service"))
  //    @Column(name = "keyword", length = 2048)
  //    private Set<String> keywords = new HashSet<>();

  /**
   * Roles authorized to read information from this service. May be reduced on a per-layer basis in
   * the "layerSettings" property. TODO: replace 'readers' with 'authorizations' for more types of
   * authorizations
   */
  //    @ElementCollection
  //    @CollectionTable(joinColumns = @JoinColumn(name = "geo_service"))
  //    @Column(name = "role_name")
  //    private Set<String> readers = new HashSet<>();

  // <editor-fold desc="getters and setters">
  public Long getId() {
    return id;
  }

  public GeoService setId(Long id) {
    this.id = id;
    return this;
  }

  public String getAdminComments() {
    return adminComments;
  }

  public GeoService setAdminComments(String adminComments) {
    this.adminComments = adminComments;
    return this;
  }

  public String getProtocol() {
    return protocol;
  }

  public GeoService setProtocol(String protocol) {
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

  public JsonNode getAuthentication() {
    return authentication;
  }

  public GeoService setAuthentication(JsonNode authentication) {
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

  public ServiceCaps getServiceCapabilities() {
    return serviceCapabilities;
  }

  public GeoService setServiceCapabilities(ServiceCaps serviceCapabilities) {
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

  public JsonNode getTileServiceInfo() {
    return tileServiceInfo;
  }

  public GeoService setTileServiceInfo(JsonNode tileServiceInfo) {
    this.tileServiceInfo = tileServiceInfo;
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
            .protocol(Service.ProtocolEnum.fromValue(protocol))
            .serverType(serverTypeEnum);

    if (Objects.equals(protocol, Service.ProtocolEnum.WMTS.getValue())) {
      // Frontend requires WMTS capabilities to parse TilingMatrix, but WMS capabilities aren't used
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
}

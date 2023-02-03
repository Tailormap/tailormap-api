/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import nl.b3p.tailormap.api.persistence.json.GeoServiceLayer;
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

  @Basic(optional = false)
  private String protocol;

  /**
   * The URL from which the capabilities of this service can be loaded and the URL to use for the
   * service, except when the advertisedUrl should be used by explicit user request. TODO: never use
   * URL in capabilities? TODO: explicitly specify relative URLs can be used? Or even if it should
   * be automatically converted to a relative URL if the hostname/port matches our URL? TODO: what
   * to do with parameters such as VERSION in the URL?
   */
  @Basic(optional = false)
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
  @Basic(optional = false)
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
  private JsonNode settings;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode layerSettings;

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

  public void setId(Long id) {
    this.id = id;
  }

  public String getAdminComments() {
    return adminComments;
  }

  public void setAdminComments(String adminComments) {
    this.adminComments = adminComments;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public ServiceCaps getServiceCapabilities() {
    return serviceCapabilities;
  }

  public GeoService setServiceCapabilities(ServiceCaps serviceCapabilities) {
    this.serviceCapabilities = serviceCapabilities;
    return this;
  }

  public JsonNode getAuthentication() {
    return authentication;
  }

  public void setAuthentication(JsonNode authentication) {
    this.authentication = authentication;
  }

  public byte[] getCapabilities() {
    return capabilities;
  }

  public void setCapabilities(byte[] capabilities) {
    this.capabilities = capabilities;
  }

  public String getCapabilitiesContentType() {
    return capabilitiesContentType;
  }

  public void setCapabilitiesContentType(String capabilitiesContentType) {
    this.capabilitiesContentType = capabilitiesContentType;
  }

  public Instant getCapabilitiesFetched() {
    return capabilitiesFetched;
  }

  public void setCapabilitiesFetched(Instant capabilitiesFetched) {
    this.capabilitiesFetched = capabilitiesFetched;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getAdvertisedUrl() {
    return advertisedUrl;
  }

  public void setAdvertisedUrl(String advertisedUrl) {
    this.advertisedUrl = advertisedUrl;
  }

  public List<GeoServiceLayer> getLayers() {
    return layers;
  }

  public void setLayers(List<GeoServiceLayer> layers) {
    this.layers = layers;
  }

  public JsonNode getSettings() {
    return settings;
  }

  public void setSettings(JsonNode settings) {
    this.settings = settings;
  }

  public JsonNode getLayerSettings() {
    return layerSettings;
  }

  public void setLayerSettings(JsonNode layerSettings) {
    this.layerSettings = layerSettings;
  }

  public JsonNode getTileServiceInfo() {
    return tileServiceInfo;
  }

  public void setTileServiceInfo(JsonNode tileServiceInfo) {
    this.tileServiceInfo = tileServiceInfo;
  }

  // </editor-fold>

  public Service toJsonPojo() {
    return new Service()
        .id(this.id)
        .url(this.url)
        .name(this.title)
        .protocol(Service.ProtocolEnum.fromValue(this.protocol))
        .tilingDisabled(false)
        .tilingGutter(0)
        .serverType(Service.ServerTypeEnum.AUTO);
  }
}

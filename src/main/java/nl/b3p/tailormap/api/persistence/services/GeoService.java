/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence.services;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.Basic;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;

@Entity
public class GeoService {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Basic(optional = false)
    private String protocol;

    /**
     * The URL from which the capabilities of this service can be loaded and the URL to use for the
     * service, except when the advertisedUrl should be used by explicit user request. TODO: never
     * use URL in capabilities? TODO: explicitly specify relative URLs can be used? Or even if it
     * should be automatically converted to a relative URL if the hostname/port matches our URL?
     * TODO: what to do with parameters such as VERSION in the URL?
     */
    @Basic(optional = false)
    private String url;

    @Column(columnDefinition = "jsonb")
    private Object authentication;

    /**
     * Original capabilities as received from the service. This can be used for capability
     * information not already parsed in this entity, such as tiling information.
     */
    @Lob
    @Basic(fetch = FetchType.LAZY)
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
    private String title;

    /**
     * A service may advertise a URL in its capabilities which does not actually work, for example
     * if the service is behind a proxy. Usually this shouldn't be used.
     */
    @Basic private String advertisedUrl;

    @Column(columnDefinition = "jsonb")
    private Object layers;

    /**
     * Server settings relevant for Tailormap use cases, such as configuring the specific server
     * type for vendor-specific capabilities etc.
     */
    @Column(columnDefinition = "jsonb")
    private Object serverSettings;

    @Column(columnDefinition = "jsonb")
    private Object layerSettings;

    @Column(columnDefinition = "jsonb")
    private Object tileServiceInfo;

    @ElementCollection
    @CollectionTable(joinColumns = @JoinColumn(name = "geo_service"))
    @Column(name = "keyword")
    private Set<String> keywords = new HashSet<>();

    /**
     * Roles authorized to read information from this service. May be reduced on a per-layer basis
     * in the "layerSettings" property.
     */
    @ElementCollection
    @CollectionTable(joinColumns = @JoinColumn(name = "geo_service"))
    @Column(name = "role_name")
    private Set<String> readers = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Object getAuthentication() {
        return authentication;
    }

    public void setAuthentication(Object authentication) {
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

    public Object getLayers() {
        return layers;
    }

    public void setLayers(Object layers) {
        this.layers = layers;
    }

    public Object getServerSettings() {
        return serverSettings;
    }

    public void setServerSettings(Object serverSettings) {
        this.serverSettings = serverSettings;
    }

    public Object getLayerSettings() {
        return layerSettings;
    }

    public void setLayerSettings(Object layerSettings) {
        this.layerSettings = layerSettings;
    }

    public Object getTileServiceInfo() {
        return tileServiceInfo;
    }

    public void setTileServiceInfo(Object tileServiceInfo) {
        this.tileServiceInfo = tileServiceInfo;
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(Set<String> keywords) {
        this.keywords = keywords;
    }

    public Set<String> getReaders() {
        return readers;
    }

    public void setReaders(Set<String> readers) {
        this.readers = readers;
    }
}

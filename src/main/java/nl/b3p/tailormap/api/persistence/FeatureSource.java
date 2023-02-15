/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.json.ServiceCaps;
import org.hibernate.annotations.Type;

@Entity
public class FeatureSource {

  public enum Protocol {
    WFS("wfs"),

    JDBC("jdbc");

    private final String value;

    Protocol(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    public static FeatureSource.Protocol fromValue(String value) {
      for (FeatureSource.Protocol p : FeatureSource.Protocol.values()) {
        if (p.value.equals(value)) {
          return p;
        }
      }
      throw new IllegalArgumentException("Unexpected value '" + value + "'");
    }
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  private Long version;

  @Column(columnDefinition = "text")
  private String notes;

  @Basic
  @NotNull
  @Enumerated(EnumType.STRING)
  private FeatureSource.Protocol protocol;

  @Basic @NotNull private String title;

  @Basic
  @NotNull
  @Column(length = 2048)
  private String url;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode authentication;

  @ManyToOne
  @JoinColumn(name = "linked_service")
  private GeoService linkedService;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private ServiceCaps serviceCapabilities;

  @OneToMany(cascade = CascadeType.ALL)
  @JoinTable(
      name = "feature_source_feature_types",
      inverseJoinColumns = @JoinColumn(name = "feature_type"),
      joinColumns = @JoinColumn(name = "feature_source", referencedColumnName = "id"))
  @OrderColumn(name = "list_index")
  private List<FeatureType> featureTypes = new ArrayList<>();

  // <editor-fold desc="getters and setters">
  public Long getId() {
    return id;
  }

  public FeatureSource setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public FeatureSource setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getNotes() {
    return notes;
  }

  public FeatureSource setNotes(String notes) {
    this.notes = notes;
    return this;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  public FeatureSource setProtocol(Protocol protocol) {
    this.protocol = protocol;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public FeatureSource setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getUrl() {
    return url;
  }

  public FeatureSource setUrl(String url) {
    this.url = url;
    return this;
  }

  public JsonNode getAuthentication() {
    return authentication;
  }

  public FeatureSource setAuthentication(JsonNode authentication) {
    this.authentication = authentication;
    return this;
  }

  public GeoService getLinkedService() {
    return linkedService;
  }

  public FeatureSource setLinkedService(GeoService linkedService) {
    this.linkedService = linkedService;
    return this;
  }

  public ServiceCaps getServiceCapabilities() {
    return serviceCapabilities;
  }

  public FeatureSource setServiceCapabilities(ServiceCaps serviceCapabilities) {
    this.serviceCapabilities = serviceCapabilities;
    return this;
  }

  public List<FeatureType> getFeatureTypes() {
    return featureTypes;
  }

  public FeatureSource setFeatureTypes(List<FeatureType> featureTypes) {
    this.featureTypes = featureTypes;
    return this;
  }
  // </editor-fold>
}

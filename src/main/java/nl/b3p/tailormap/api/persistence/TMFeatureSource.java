/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import nl.b3p.tailormap.api.persistence.json.JDBCConnectionProperties;
import nl.b3p.tailormap.api.persistence.json.ServiceAuthentication;
import nl.b3p.tailormap.api.persistence.json.TMServiceCaps;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "feature_source")
@EntityListeners(EntityEventPublisher.class)
public class TMFeatureSource {

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

    public static TMFeatureSource.Protocol fromValue(String value) {
      for (TMFeatureSource.Protocol p : TMFeatureSource.Protocol.values()) {
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

  @Version private Long version;

  @Transient private boolean refreshCapabilities;

  @Column(columnDefinition = "text")
  private String notes;

  @NotNull
  @Enumerated(EnumType.STRING)
  private TMFeatureSource.Protocol protocol;

  @Basic private String title;

  @Column(length = 2048)
  private String url;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private JDBCConnectionProperties jdbcConnection;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private ServiceAuthentication authentication;

  @ManyToOne
  @JoinColumn(name = "linked_service")
  private GeoService linkedService;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private TMServiceCaps serviceCapabilities;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinTable(
      name = "feature_source_feature_types",
      inverseJoinColumns = @JoinColumn(name = "feature_type"),
      joinColumns = @JoinColumn(name = "feature_source", referencedColumnName = "id"))
  @OrderBy("name asc")
  private List<TMFeatureType> featureTypes = new ArrayList<>();

  @Override
  public String toString() {
    try {
      return "TMFeatureSource{"
          + "id="
          + id
          + ", protocol="
          + protocol
          + ", title='"
          + title
          + '\''
          + ", url='"
          + url
          + '\''
          + ", jdbcConnection="
          + new ObjectMapper().writeValueAsString(jdbcConnection)
          + '}';
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  // <editor-fold desc="getters and setters">
  public Long getId() {
    return id;
  }

  public TMFeatureSource setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public TMFeatureSource setVersion(Long version) {
    this.version = version;
    return this;
  }

  public boolean isRefreshCapabilities() {
    return refreshCapabilities;
  }

  public void setRefreshCapabilities(boolean refreshCapabilities) {
    this.refreshCapabilities = refreshCapabilities;
  }

  public String getNotes() {
    return notes;
  }

  public TMFeatureSource setNotes(String notes) {
    this.notes = notes;
    return this;
  }

  public Protocol getProtocol() {
    return protocol;
  }

  public TMFeatureSource setProtocol(Protocol protocol) {
    this.protocol = protocol;
    return this;
  }

  public String getTitle() {
    return title;
  }

  public TMFeatureSource setTitle(String title) {
    this.title = title;
    return this;
  }

  public String getUrl() {
    return url;
  }

  public TMFeatureSource setUrl(String url) {
    this.url = url;
    return this;
  }

  public JDBCConnectionProperties getJdbcConnection() {
    return jdbcConnection;
  }

  public TMFeatureSource setJdbcConnection(JDBCConnectionProperties jdbcConnection) {
    this.jdbcConnection = jdbcConnection;
    return this;
  }

  public ServiceAuthentication getAuthentication() {
    return authentication;
  }

  public TMFeatureSource setAuthentication(ServiceAuthentication authentication) {
    this.authentication = authentication;
    return this;
  }

  public GeoService getLinkedService() {
    return linkedService;
  }

  public TMFeatureSource setLinkedService(GeoService linkedService) {
    this.linkedService = linkedService;
    return this;
  }

  public TMServiceCaps getServiceCapabilities() {
    return serviceCapabilities;
  }

  public TMFeatureSource setServiceCapabilities(TMServiceCaps serviceCapabilities) {
    this.serviceCapabilities = serviceCapabilities;
    return this;
  }

  // Added this extra getter to work around Spring Data Rest
  // By adding a repository for feature types SDR does not add feature types when fetching a feature
  // source, while the front-end currently depends on having all feature types available.
  // In the future we could refactor this to give only a list of names and fetch the type itself
  // when needed.
  public List<TMFeatureType> getAllFeatureTypes() {
    return featureTypes;
  }

  public List<TMFeatureType> getFeatureTypes() {
    return featureTypes;
  }

  public TMFeatureSource setFeatureTypes(List<TMFeatureType> featureTypes) {
    this.featureTypes = featureTypes;
    return this;
  }

  // </editor-fold>

  public TMFeatureType findFeatureTypeByName(String featureTypeName) {
    return getFeatureTypes().stream()
        .filter(ft -> featureTypeName.equals(ft.getName()))
        .findFirst()
        .orElse(null);
  }
}

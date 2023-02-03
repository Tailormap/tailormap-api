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
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OrderColumn;
import org.hibernate.annotations.Type;

@Entity
public class FeatureSource {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(columnDefinition = "text")
  private String adminComments;

  @Basic(optional = false)
  private String protocol;

  @Basic(optional = false)
  private String title;

  @Basic(optional = false)
  @Column(length = 2048)
  private String url;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode authentication;

  @ManyToOne
  @JoinColumn(name = "linked_service")
  private GeoService linkedService;

  @ManyToMany(cascade = CascadeType.ALL) // Actually @OneToMany, workaround for HHH-1268
  @JoinTable(
      name = "feature_source_feature_types",
      inverseJoinColumns = @JoinColumn(name = "feature_type"),
      joinColumns = @JoinColumn(name = "feature_source", referencedColumnName = "id"))
  @OrderColumn(name = "list_index")
  private List<FeatureType> featureTypes = new ArrayList<>();

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

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public JsonNode getAuthentication() {
    return authentication;
  }

  public void setAuthentication(JsonNode authentication) {
    this.authentication = authentication;
  }

  public GeoService getLinkedService() {
    return linkedService;
  }

  public void setLinkedService(GeoService linkedService) {
    this.linkedService = linkedService;
  }

  public List<FeatureType> getFeatureTypes() {
    return featureTypes;
  }

  public void setFeatureTypes(List<FeatureType> featureTypes) {
    this.featureTypes = featureTypes;
  }
}

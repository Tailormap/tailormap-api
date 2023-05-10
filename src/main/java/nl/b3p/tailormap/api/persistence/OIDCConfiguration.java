/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Version;
import javax.validation.constraints.NotNull;

@Entity
public class OIDCConfiguration {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @NotNull private String name;

  @NotNull private String clientId;

  private String clientSecret;

  @NotNull private String issuerUrl;

  public Long getId() {
    return id;
  }

  public OIDCConfiguration setId(Long id) {
    this.id = id;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public OIDCConfiguration setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getName() {
    return name;
  }

  public OIDCConfiguration setName(String name) {
    this.name = name;
    return this;
  }

  public String getClientId() {
    return clientId;
  }

  public OIDCConfiguration setClientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public OIDCConfiguration setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
    return this;
  }

  public String getIssuerUrl() {
    return issuerUrl;
  }

  public OIDCConfiguration setIssuerUrl(String issuerUrl) {
    this.issuerUrl = issuerUrl;
    return this;
  }
}

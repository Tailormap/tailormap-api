/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class OIDCConfiguration {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  @NotNull private String name;

  @NotNull private String clientId;

  private String clientSecret;

  @NotNull private String issuerUrl;

  @NotNull private String userNameAttribute;

  private String status;

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

  public String getUserNameAttribute() {
    return userNameAttribute;
  }

  public OIDCConfiguration setUserNameAttribute(String userNameAttribute) {
    this.userNameAttribute = userNameAttribute;
    return this;
  }

  public String getStatus() {
    return status;
  }

  public OIDCConfiguration setStatus(String status) {
    this.status = status;
    return this;
  }
}

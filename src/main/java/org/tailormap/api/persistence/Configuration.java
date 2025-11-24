/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.hibernate.annotations.Type;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.tailormap.api.persistence.listener.EntityEventPublisher;
import tools.jackson.databind.JsonNode;

@Entity
@EntityListeners({EntityEventPublisher.class, AuditingEntityListener.class})
public class Configuration extends AuditMetadata {
  public static final String DEFAULT_APP = "default-app";

  public static final String DEFAULT_BASE_APP = "default-base-app";

  public static final String HOME_PAGE = "home-page";

  public static final String PORTAL_MENU = "portal-menu";

  @Id
  private String key;

  @Version
  private Long version;

  @Column(columnDefinition = "text")
  private String value;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private JsonNode jsonValue;

  private boolean availableForViewer;

  // <editor-fold desc="getters and setters">
  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Long getVersion() {
    return version;
  }

  public Configuration setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public JsonNode getJsonValue() {
    return jsonValue;
  }

  public void setJsonValue(JsonNode jsonValue) {
    this.jsonValue = jsonValue;
  }

  public boolean isAvailableForViewer() {
    return availableForViewer;
  }

  public void setAvailableForViewer(boolean availableForViewer) {
    this.availableForViewer = availableForViewer;
  }
  // </editor-fold>
}

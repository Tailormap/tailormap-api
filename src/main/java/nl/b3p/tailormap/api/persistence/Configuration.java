/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;
import org.hibernate.annotations.Type;

@Entity
public class Configuration {
  public static final String DEFAULT_APP = "default-app";

  @Id private String key;

  @Version private Long version;

  @Column(columnDefinition = "text")
  private String value;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
  @Column(columnDefinition = "jsonb")
  private JsonNode jsonValue;

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
}

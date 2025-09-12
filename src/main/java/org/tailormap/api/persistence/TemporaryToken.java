/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "temporary_token")
public class TemporaryToken {
  @Id
  private UUID token;

  @NotNull private String username;

  @NotNull private OffsetDateTime expirationTime;

  public TemporaryToken() {}

  public TemporaryToken(String username) {
    this.username = username;
    this.expirationTime = OffsetDateTime.now(ZoneId.systemDefault());
    this.token = UUID.randomUUID();
  }

  public UUID getToken() {
    return token;
  }

  public TemporaryToken setToken(UUID token) {
    this.token = token;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public TemporaryToken setUsername(String username) {
    this.username = username;
    return this;
  }

  public OffsetDateTime getExpirationTime() {
    return expirationTime;
  }

  public TemporaryToken setExpirationTime(OffsetDateTime expirationTime) {
    this.expirationTime = expirationTime;
    return this;
  }
}

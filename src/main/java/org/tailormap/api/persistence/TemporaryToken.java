/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

@Entity
@Table(name = "temporary_token")
public class TemporaryToken {

  public enum TokenType {
    PASSWORD_RESET
    // we can add other use tokens in the future such as EMAIL_VERIFICATION
  }

  @Id
  private UUID token;

  @Enumerated(EnumType.STRING)
  @Column(columnDefinition = "varchar(18) not null")
  @NotNull private TokenType tokenType;

  @NotNull private String username;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @NotNull private ZonedDateTime expirationTime;

  public TemporaryToken() {
    // Default constructor for JPA
  }

  public TemporaryToken(TokenType tokenType, String username, int expirationMinutes) {
    this.tokenType = tokenType;
    this.username = username;

    this.expirationTime = ZonedDateTime.now(ZoneId.of("UTC")).plusMinutes(expirationMinutes);
    this.token = UUID.randomUUID();
  }

  public UUID getToken() {
    return token;
  }

  public TemporaryToken setToken(UUID token) {
    this.token = token;
    return this;
  }

  public TokenType getTokenType() {
    return tokenType;
  }

  public TemporaryToken setTokenType(TokenType tokenType) {
    this.tokenType = tokenType;
    return this;
  }

  public String getUsername() {
    return username;
  }

  public TemporaryToken setUsername(String username) {
    this.username = username;
    return this;
  }

  public ZonedDateTime getExpirationTime() {
    return expirationTime;
  }

  public TemporaryToken setExpirationTime(ZonedDateTime expirationTime) {
    this.expirationTime = expirationTime;
    return this;
  }

  public String getCombinedTokenAndExpirationAsBase64() {
    return Base64.getEncoder()
        .encodeToString((this.token + "#" + this.expirationTime.toEpochSecond()).getBytes(UTF_8));
  }
}

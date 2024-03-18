/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;
import nl.b3p.tailormap.api.util.Constants;
import nl.b3p.tailormap.api.util.TMPasswordDeserializer;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "users")
@EntityListeners(EntityEventPublisher.class)
public class User {

  @Id
  @Pattern(regexp = Constants.NAME_REGEX, message = "User" + Constants.NAME_REGEX_INVALID_MESSAGE)
  private String username;

  @Version private Long version;

  @NotNull
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  @JsonDeserialize(using = TMPasswordDeserializer.class)
  // bcrypt MAX/MIN length is 60 + {bcrypt} token, but for testing we use shorter plain text
  // passwords
  @Size(max = (8 + 60))
  private String password;

  @Email private String email;

  private String name;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(columnDefinition = "timestamp with time zone")
  private ZonedDateTime validUntil;

  private boolean enabled = true;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private JsonNode additionalProperties;

  @ManyToMany
  @JoinTable(
      name = "user_groups",
      joinColumns = @JoinColumn(name = "username"),
      inverseJoinColumns = @JoinColumn(name = "group_name"))
  private Set<Group> groups = new HashSet<>();

  public String getUsername() {
    return username;
  }

  public User setUsername(String username) {
    this.username = username;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public User setVersion(Long version) {
    this.version = version;
    return this;
  }

  public String getPassword() {
    return password;
  }

  public User setPassword(String passwordHash) {
    this.password = passwordHash;
    return this;
  }

  public String getEmail() {
    return email;
  }

  public User setEmail(String email) {
    this.email = email;
    return this;
  }

  public String getName() {
    return name;
  }

  public User setName(String name) {
    this.name = name;
    return this;
  }

  public String getNotes() {
    return notes;
  }

  public User setNotes(String notes) {
    this.notes = notes;
    return this;
  }

  public JsonNode getAdditionalProperties() {
    return additionalProperties;
  }

  public User setAdditionalProperties(JsonNode additionalProperties) {
    this.additionalProperties = additionalProperties;
    return this;
  }

  public Set<Group> getGroups() {
    return groups;
  }

  public User setGroups(Set<Group> groups) {
    this.groups = groups;
    return this;
  }

  public Set<String> getGroupNames() {
    return groups.stream().map(Group::getName).collect(java.util.stream.Collectors.toSet());
  }

  public ZonedDateTime getValidUntil() {
    return validUntil;
  }

  public User setValidUntil(ZonedDateTime validUntil) {
    this.validUntil = validUntil;
    return this;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public User setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }
}

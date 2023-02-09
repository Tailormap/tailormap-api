/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "users")
public class User {
  @Id private String username;

  private String password;

  private String email;

  private String name;

  @Column(columnDefinition = "text")
  private String notes;

  @Type(type = "io.hypersistence.utils.hibernate.type.json.JsonBinaryType")
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
}

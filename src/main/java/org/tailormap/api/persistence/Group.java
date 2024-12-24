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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.hibernate.annotations.Type;
import org.tailormap.api.persistence.json.AdminAdditionalProperty;
import org.tailormap.api.persistence.listener.EntityEventPublisher;
import org.tailormap.api.util.Constants;

@Entity
@Table(name = "groups")
@EntityListeners(EntityEventPublisher.class)
public class Group {
  // Group to make authorization rules for anonymous users
  public static final String ANONYMOUS = "anonymous";
  // Group to make authorization rules for authenticated users
  public static final String AUTHENTICATED = "authenticated";
  public static final String ADMIN = "admin";
  public static final String ACTUATOR = "actuator";

  @Id
  @Pattern(regexp = Constants.NAME_REGEX, message = "Group " + Constants.NAME_REGEX_INVALID_MESSAGE)
  private String name;

  @Version
  private Long version;

  private boolean systemGroup;

  private String description;

  @Column(columnDefinition = "text")
  private String notes;

  @ManyToMany(mappedBy = "groups")
  private Set<User> members = new HashSet<>();

  /**
   * Generic additional properties which can be set on a group. A viewer admin frontend extension component can define
   * attributes for the purposes of the extension and the viewer admin UI will show a control to edit the attribute in
   * the group detail form.
   */
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private List<AdminAdditionalProperty> additionalProperties = new ArrayList<>();

  public String getName() {
    return name;
  }

  public Group setName(String name) {
    this.name = name;
    return this;
  }

  public Long getVersion() {
    return version;
  }

  public Group setVersion(Long version) {
    this.version = version;
    return this;
  }

  public boolean isSystemGroup() {
    return systemGroup;
  }

  public Group setSystemGroup(boolean systemGroup) {
    this.systemGroup = systemGroup;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public Group setDescription(String title) {
    this.description = title;
    return this;
  }

  public String getNotes() {
    return notes;
  }

  public Group setNotes(String notes) {
    this.notes = notes;
    return this;
  }

  public Set<User> getMembers() {
    return members;
  }

  public Group setMembers(Set<User> members) {
    this.members = members;
    return this;
  }

  public List<AdminAdditionalProperty> getAdditionalProperties() {
    return additionalProperties;
  }

  public Group setAdditionalProperties(List<AdminAdditionalProperty> additionalProperties) {
    this.additionalProperties = additionalProperties;
    return this;
  }

  @PreRemove
  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void removeMembers() {
    for (User user : this.members) {
      user.getGroups().remove(this);
    }
  }
}

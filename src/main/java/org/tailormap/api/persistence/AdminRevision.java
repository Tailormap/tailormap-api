/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.hibernate.envers.DateTimeFormatter;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.ModifiedEntityNames;
import org.hibernate.envers.RevisionEntity;
import org.tailormap.api.persistence.listener.AdminRevisionListener;

/** Custom revision entity to store additional information about revisions. */
@Entity
@RevisionEntity
@EntityListeners(AdminRevisionListener.class)
@Table(name = "revisions", schema = "history")
public class AdminRevision extends DefaultRevisionEntity {
  private String modifiedBy;

  @ElementCollection
  @JoinTable(name = "modified_entities", schema = "history", joinColumns = @JoinColumn(name = "revision_number"))
  @Column(name = "modified_entity_name")
  @ModifiedEntityNames
  private Set<String> modifiedEntityNames = new HashSet<>();

  public String getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(String modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Set<String> getModifiedEntityNames() {
    return modifiedEntityNames;
  }

  public void setModifiedEntityNames(Set<String> modifiedEntityNames) {
    this.modifiedEntityNames = modifiedEntityNames;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AdminRevision that)) return false;
    if (!super.equals(o)) return false;

    return Objects.equals(modifiedBy, that.modifiedBy);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(modifiedBy);
    return result;
  }

  @Override
  public String toString() {
    return "AdminRevision{id = " + getId() + ", revisionDate = "
        + DateTimeFormatter.INSTANCE.format(getRevisionDate()) + ", modifiedBy='"
        + modifiedBy + '\'' + ", modifiedEntityNames="
        + String.join(", ", modifiedEntityNames) + '}';
  }
}

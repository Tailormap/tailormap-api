/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Table;
import java.util.Objects;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.RevisionEntity;
import org.tailormap.api.persistence.listener.AdminRevisionListener;

@Entity
@RevisionEntity
@EntityListeners(AdminRevisionListener.class)
@Table(name = "admin_revisions", schema = "history")
public class AdminRevisionEntity extends DefaultRevisionEntity {
  private String modifiedBy;

  @Column(columnDefinition = "text")
  private String summary;

  public String getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(String modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AdminRevisionEntity that)) return false;
    if (!super.equals(o)) return false;

    return Objects.equals(modifiedBy, that.modifiedBy) && Objects.equals(summary, that.summary);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + Objects.hashCode(modifiedBy);
    result = 31 * result + Objects.hashCode(summary);
    return result;
  }
}

/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Type;
import org.springframework.format.annotation.DateTimeFormat;
import org.tailormap.api.admin.model.TaskSchedule;

/** SearchIndex is a table that stores the metadata for search indexes for a feature type. */
@Entity
@Table(name = "search_index")
public class SearchIndex implements Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String name;

  private Long featureTypeId;

  /** List of attribute names that were used when building the search index. */
  @JsonProperty("searchFieldsUsed")
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Valid
  private List<String> searchFieldsUsed = new ArrayList<>();

  /** List of attribute names for display that were used when building the search index. */
  @JsonProperty("searchDisplayFieldsUsed")
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Valid
  private List<String> searchDisplayFieldsUsed = new ArrayList<>();

  @Column(columnDefinition = "text")
  private String comment;

  /** Date and time of last index creation. */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Valid
  @JsonProperty("lastIndexed")
  private OffsetDateTime lastIndexed;

  @Valid
  @JsonProperty("schedule")
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private TaskSchedule schedule;

  @Enumerated(EnumType.STRING)
  @NotNull
  @Column(columnDefinition = "varchar(8) default 'INITIAL'")
  private SearchIndex.Status status = SearchIndex.Status.INITIAL;

  public Long getId() {
    return id;
  }

  public SearchIndex setId(Long id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public SearchIndex setName(String name) {
    this.name = name;
    return this;
  }

  public Long getFeatureTypeId() {
    return featureTypeId;
  }

  public SearchIndex setFeatureTypeId(Long featureTypeId) {
    this.featureTypeId = featureTypeId;
    return this;
  }

  public List<String> getSearchFieldsUsed() {
    return searchFieldsUsed;
  }

  public SearchIndex setSearchFieldsUsed(List<String> searchFieldsUsed) {
    this.searchFieldsUsed = searchFieldsUsed;
    return this;
  }

  public List<String> getSearchDisplayFieldsUsed() {
    return searchDisplayFieldsUsed;
  }

  public SearchIndex setSearchDisplayFieldsUsed(List<String> searchDisplayFieldsUsed) {
    this.searchDisplayFieldsUsed = searchDisplayFieldsUsed;
    return this;
  }

  public String getComment() {
    return comment;
  }

  public SearchIndex setComment(String comment) {
    this.comment = comment;
    return this;
  }

  public OffsetDateTime getLastIndexed() {
    return lastIndexed;
  }

  public SearchIndex setLastIndexed(OffsetDateTime lastIndexed) {
    this.lastIndexed = lastIndexed;
    return this;
  }

  public Status getStatus() {
    return status;
  }

  public SearchIndex setStatus(Status status) {
    this.status = status;
    return this;
  }

  public @Valid TaskSchedule getSchedule() {
    return schedule;
  }

  public void setSchedule(@Valid TaskSchedule schedule) {
    this.schedule = schedule;
  }

  public enum Status {
    INITIAL("initial"),
    INDEXING("indexing"),
    INDEXED("indexed"),
    ERROR("error");
    private final String value;

    Status(String value) {
      this.value = value;
    }

    public static SearchIndex.Status fromValue(String value) {
      for (SearchIndex.Status status : SearchIndex.Status.values()) {
        if (status.value.equals(value)) {
          return status;
        }
      }
      throw new IllegalArgumentException("Unexpected value '%s'".formatted(value));
    }

    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }
}

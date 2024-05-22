/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

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

/** SearchIndex is a table that stores the metadata for search indexes for a feature type. */
@Entity
@Table(name = "search_index")
public class SearchIndex implements Serializable {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String name;

  private Long featureTypeId;

  /** List of attribute names that were used last last when building the search index. */
  @JsonProperty("searchFieldsUsed")
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Valid
  private List<String> searchFieldsUsed;

  /** List of attribute names for display that were used last when building the search index. */
  @JsonProperty("searchDisplayFieldsUsed")
  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  @Valid
  private List<String> searchDisplayFieldsUsed;

  @Column(columnDefinition = "text")
  private String comment;

  /** Date and time of last index creation. */
  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  @Valid
  @JsonProperty("lastIndexed")
  private OffsetDateTime lastIndexed;

  public enum Status {
    INITIAL("initial"),
    INDEXING("indexing"),
    INDEXED("indexed"),
    ERROR("error");
    private final String value;

    Status(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }

    public static SearchIndex.Status fromValue(String value) {
      for (SearchIndex.Status status : SearchIndex.Status.values()) {
        if (status.value.equals(value)) {
          return status;
        }
      }
      throw new IllegalArgumentException("Unexpected value '%s'".formatted(value));
    }
  }

  @Enumerated(EnumType.STRING)
  @NotNull
  @Column(columnDefinition = "varchar(8) default 'INITIAL'")
  private SearchIndex.Status status = SearchIndex.Status.INITIAL;

  public SearchIndex id(Long id) {
    this.id = id;
    return this;
  }

  public Long getId() {
    return this.id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public SearchIndex featureTypeId(Long featureTypeName) {
    this.featureTypeId = featureTypeName;
    return this;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getFeatureTypeId() {
    return this.featureTypeId;
  }

  public void setFeatureTypeId(Long featureTypeName) {
    this.featureTypeId = featureTypeName;
  }

  public String getComment() {
    return this.comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public SearchIndex searchFieldsUsed(List<String> searchFields) {
    this.searchFieldsUsed = searchFields;
    return this;
  }

  public SearchIndex addSearchFieldsUsedItem(String searchFieldsItem) {
    if (this.searchFieldsUsed == null) {
      this.searchFieldsUsed = new ArrayList<>();
    }
    this.searchFieldsUsed.add(searchFieldsItem);
    return this;
  }

  public List<String> getSearchFieldsUsed() {
    return this.searchFieldsUsed;
  }

  public void setSearchFieldsUsed(List<String> searchFields) {
    this.searchFieldsUsed = searchFields;
  }

  public SearchIndex searchDisplayFieldsUsed(List<String> searchDisplayFields) {
    this.searchDisplayFieldsUsed = searchDisplayFields;
    return this;
  }

  public SearchIndex addSearchDisplayFieldsUsedItem(String searchDisplayFieldsItem) {
    if (this.searchDisplayFieldsUsed == null) {
      this.searchDisplayFieldsUsed = new ArrayList<>();
    }
    this.searchDisplayFieldsUsed.add(searchDisplayFieldsItem);
    return this;
  }

  public List<String> getSearchDisplayFieldsUsed() {
    return searchDisplayFieldsUsed;
  }

  public void setSearchDisplayFieldsUsed(List<String> searchDisplayFields) {
    this.searchDisplayFieldsUsed = searchDisplayFields;
  }

  public SearchIndex status(SearchIndex.Status status) {
    this.status = status;
    return this;
  }

  public SearchIndex.Status getStatus() {
    return this.status;
  }

  public void setStatus(SearchIndex.Status status) {
    this.status = status;
  }

  public SearchIndex lastIndexed(OffsetDateTime lastIndexed) {
    this.lastIndexed = lastIndexed;
    return this;
  }

  public OffsetDateTime getLastIndexed() {
    return this.lastIndexed;
  }

  public void setLastIndexed(OffsetDateTime lastIndexed) {
    this.lastIndexed = lastIndexed;
  }
}

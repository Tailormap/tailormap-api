/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Type;
import org.tailormap.api.persistence.json.PageTile;
import org.tailormap.api.persistence.listener.EntityEventPublisher;
import org.tailormap.api.viewer.model.PageResponse;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class Page {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private Long version;

  private String type;

  @Column(unique = true)
  @NotNull
  private String name;

  private String title;

  @Column(columnDefinition = "text")
  private String content;

  private String className;

  @Type(value = io.hypersistence.utils.hibernate.type.json.JsonBinaryType.class)
  @Column(columnDefinition = "jsonb")
  private List<PageTile> tiles = new ArrayList<>();

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getVersion() {
    return version;
  }

  public void setVersion(Long version) {
    this.version = version;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getClassName() {
    return className;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public List<PageTile> getTiles() {
    return tiles;
  }

  public void setTiles(List<PageTile> tiles) {
    this.tiles = tiles;
  }

  @JsonIgnore
  public PageResponse getPageResponse() {
    return new PageResponse()
        .id(getId())
        .type(getType())
        .name(getName())
        .title(getTitle())
        .content(getContent())
        .className(getClassName());
  }
}

/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.persistence;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.OffsetDateTime;
import java.util.UUID;
import nl.b3p.tailormap.api.persistence.listener.EntityEventPublisher;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class Upload {
  public static final String CATEGORY_APP_LOGO = "app-logo";
  public static final String CATEGORY_LEGEND = "legend";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String category;

  private String filename;

  private String mimeType;

  private Integer imageWidth;

  private Integer imageHeight;

  private Boolean hiDpiImage;

  private OffsetDateTime lastModified;

  @Basic(fetch = FetchType.LAZY)
  private byte[] content;

  public int getContentLength() {
    return getContent() == null ? 0 : content.length;
  }

  public UUID getId() {
    return id;
  }

  public Upload setId(UUID id) {
    this.id = id;
    return this;
  }

  public String getCategory() {
    return category;
  }

  public Upload setCategory(String category) {
    this.category = category;
    return this;
  }

  public String getFilename() {
    return filename;
  }

  public Upload setFilename(String filename) {
    this.filename = filename;
    return this;
  }

  public String getMimeType() {
    return mimeType;
  }

  public Upload setMimeType(String mimeType) {
    this.mimeType = mimeType;
    return this;
  }

  public Integer getImageWidth() {
    return imageWidth;
  }

  public Upload setImageWidth(Integer imageWidth) {
    this.imageWidth = imageWidth;
    return this;
  }

  public Integer getImageHeight() {
    return imageHeight;
  }

  public Upload setImageHeight(Integer imageHeight) {
    this.imageHeight = imageHeight;
    return this;
  }

  public Boolean getHiDpiImage() {
    return hiDpiImage;
  }

  public Upload setHiDpiImage(Boolean hiDpiImage) {
    this.hiDpiImage = hiDpiImage;
    return this;
  }

  public OffsetDateTime getLastModified() {
    return lastModified;
  }

  public Upload setLastModified(OffsetDateTime lastModified) {
    this.lastModified = lastModified;
    return this;
  }

  public byte[] getContent() {
    return content;
  }

  public Upload setContent(byte[] content) {
    this.content = content;
    return this;
  }
}

/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.persistence;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.tailormap.api.persistence.listener.EntityEventPublisher;

@Entity
@EntityListeners(EntityEventPublisher.class)
public class Upload {
  public static final String CATEGORY_APP_LOGO = "app-logo";
  public static final String CATEGORY_LEGEND = "legend";
  public static final String CATEGORY_PORTAL_IMAGE = "portal-image";
  public static final String CATEGORY_DRAWING_STYLE = "drawing-style";
  public static final String CATEGORY_DRAWING_STYLE_IMAGE = "drawing-style-image";
  public static final String CATEGORY_SSO_IMAGE = "sso-image";

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  private String category;

  private String filename;

  private String mimeType;

  private Integer imageWidth;

  private Integer imageHeight;

  private Boolean hiDpiImage;

  @NotNull private OffsetDateTime lastModified = OffsetDateTime.now(ZoneId.systemDefault());

  @Basic(fetch = FetchType.LAZY)
  private byte[] content;

  private String hash;

  // <editor-fold desc="getters and setters">
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

  public String getHash() {
    return hash;
  }

  public Upload setHash(String hash) {
    this.hash = hash;
    return this;
  }
  // </editor-fold>

  @PrePersist
  @PreUpdate
  public void computeHash() {
    if (content != null) {
      this.hash = DigestUtils.sha1Hex(content);
    } else {
      this.hash = null;
    }
  }
}

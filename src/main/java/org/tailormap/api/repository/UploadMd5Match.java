/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.repository;

import java.util.Objects;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;

public final class UploadMd5Match {
  private final UUID id;
  private final String hash;

  public UploadMd5Match(UUID id, byte[] content) {
    this.id = id;
    this.hash = content == null ? null : DigestUtils.md5Hex(content);
  }

  public UUID getId() {
    return id;
  }

  public String getHash() {
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (UploadMd5Match) obj;
    return Objects.equals(this.id, that.id) && Objects.equals(this.hash, that.hash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, hash);
  }

  @Override
  public String toString() {
    return "UploadMd5Match[" + "id=" + id + ", " + "hash=" + hash + ']';
  }
}

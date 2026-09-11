/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import jakarta.persistence.EnumeratedValue;

public enum UploadCategory {
  LEGEND("legend", true),
  LAYER_ATTACHED_FILE("layer-attached-file", true),

  APP_LOGO("app-logo", false),
  HEADER_LOGO("header-logo", false),
  IMAGE("image", false),
  PORTAL_IMAGE("portal-image", false),
  DRAWING_STYLE("drawing-style", false),
  DRAWING_STYLE_IMAGE("drawing-style-image", false),
  SSO_IMAGE("sso-image", false),
  THEME_THEME_LOGO("theme-theme-logo", false),
  THEME_FAVICON("theme-favicon", false),
  UNRESTRICTED("unrestricted", false);

  @EnumeratedValue
  private final String value;

  private final boolean restricted;

  UploadCategory(String value, boolean restricted) {
    this.value = value;
    this.restricted = restricted;
  }

  public String getValue() {
    return value;
  }

  /**
   * Returns the string representation of the enum value.
   *
   * @return the string representation of the enum value
   * @see #getValue()
   */
  @Override
  public String toString() {
    return getValue();
  }

  public boolean isRestricted() {
    return restricted;
  }
}

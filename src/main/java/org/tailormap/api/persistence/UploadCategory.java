/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.persistence;

import jakarta.persistence.EnumeratedValue;
import java.util.List;

public enum UploadCategory {
  LEGEND("legend"),
  LAYER_ATTACHED_FILE("layer-attached-file"),
  APP_LOGO("app-logo"),
  HEADER_LOGO("header-logo"),
  PORTAL_IMAGE("portal-image"),
  DRAWING_STYLE("drawing-style"),
  DRAWING_STYLE_IMAGE("drawing-style-image"),
  SSO_IMAGE("sso-image"),
  THEME_THEME_LOGO("theme-theme-logo"),
  THEME_FAVICON("theme-favicon"),
  UNRESTRICTED("unrestricted");

  @EnumeratedValue
  private final String value;

  UploadCategory(String value) {
    this.value = value;
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

  public static List<UploadCategory> getUnrestrictedCategories() {
    return List.of(
        APP_LOGO,
        // TODO move LEGEND to restricted categories when we have a new controller providing access to
        //   restricted uploads
        LEGEND,
        PORTAL_IMAGE,
        DRAWING_STYLE,
        DRAWING_STYLE_IMAGE,
        SSO_IMAGE,
        THEME_THEME_LOGO,
        THEME_FAVICON,
        UNRESTRICTED);
  }

  public static List<UploadCategory> getRestrictedCategories() {
    return List.of(
        LAYER_ATTACHED_FILE
        // TODO add LEGEND to restricted categories when we have a new controller providing access to
        //   restricted uploads
        // , LEGEND
        );
  }
}

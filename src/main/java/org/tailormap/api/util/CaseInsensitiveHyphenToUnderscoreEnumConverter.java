/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import java.util.Locale;
import org.springframework.core.convert.converter.Converter;

/**
 * A utility class to convert a string to an enum in a case-insensitive way, replacing hyphens with underscores. Because
 * Angular/TypeScript prefers using eg. "layer-attached-file" instead of "LAYER_ATTACHED_FILE" for enum values, this
 * converter allows for a more user-friendly way to convert strings to enums.
 *
 * @param <T> the enum type
 */
public class CaseInsensitiveHyphenToUnderscoreEnumConverter<T extends Enum<T>> implements Converter<String, T> {
  private final Class<T> enumClass;

  public CaseInsensitiveHyphenToUnderscoreEnumConverter(Class<T> enumClass) {
    this.enumClass = enumClass;
  }

  @Override
  public T convert(String from) {
    return Enum.valueOf(enumClass, from.toUpperCase(Locale.ROOT).replace('-', '_'));
  }
}

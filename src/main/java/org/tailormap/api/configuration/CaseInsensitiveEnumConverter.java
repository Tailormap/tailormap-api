/*
 * Copyright (C) 2024 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import java.util.Locale;
import org.springframework.core.convert.converter.Converter;

/**
 * A utility class to convert a string to an enum in a case-insensitive way.
 *
 * @param <T> the enum type
 */
public class CaseInsensitiveEnumConverter<T extends Enum<T>> implements Converter<String, T> {
  private final Class<T> enumClass;

  public CaseInsensitiveEnumConverter(Class<T> enumClass) {
    this.enumClass = enumClass;
  }

  @Override
  public T convert(String from) {
    return Enum.valueOf(enumClass, from.toUpperCase(Locale.ROOT));
  }
}

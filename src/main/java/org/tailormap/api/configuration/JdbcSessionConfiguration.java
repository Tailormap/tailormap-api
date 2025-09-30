/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration;

import java.io.InputStream;
import java.io.ObjectInputStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Ignore deserialization exceptions, see <a
 * href="https://github.com/spring-projects/spring-session/issues/529#issuecomment-2761671945">here</a>.
 */
@Configuration
@EnableJdbcHttpSession
@Profile("!test")
public class JdbcSessionConfiguration {

  @Bean("springSessionConversionService")
  public ConversionService springSessionConversionService() {
    GenericConversionService converter = new GenericConversionService();
    converter.addConverter(Object.class, byte[].class, new SerializingConverter());
    converter.addConverter(byte[].class, Object.class, new DeserializingConverter(new CustomDeserializer()));
    return converter;
  }

  static class CustomDeserializer implements Deserializer<Object> {
    @Override
    public Object deserialize(InputStream inputStream) {
      try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
        return ois.readObject();
      } catch (Exception ignored) {
        return null;
      }
    }
  }
}

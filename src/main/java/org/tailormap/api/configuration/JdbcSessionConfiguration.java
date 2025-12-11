/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.Deserializer;
import org.springframework.core.serializer.Serializer;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Ignore deserialization exceptions, see <a
 * href="https://github.com/spring-projects/spring-session/issues/529#issuecomment-2761671945">here</a>.
 */
@Configuration(proxyBeanMethods = false)
public class JdbcSessionConfiguration implements BeanClassLoaderAware {
  private static final String CREATE_SESSION_ATTRIBUTE_QUERY =
      """
INSERT INTO %TABLE_NAME%_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
VALUES (?, ?, convert_from(?, 'UTF8')::jsonb)
""";

  private static final String UPDATE_SESSION_ATTRIBUTE_QUERY =
      """
UPDATE %TABLE_NAME%_ATTRIBUTES
SET ATTRIBUTE_BYTES = encode(?, 'escape')::jsonb
WHERE SESSION_PRIMARY_ID = ?
AND ATTRIBUTE_NAME = ?
""";

  private ClassLoader classLoader;

  @Bean
  SessionRepositoryCustomizer<JdbcIndexedSessionRepository> customizer() {
    return (sessionRepository) -> {
      sessionRepository.setCreateSessionAttributeQuery(CREATE_SESSION_ATTRIBUTE_QUERY);
      sessionRepository.setUpdateSessionAttributeQuery(UPDATE_SESSION_ATTRIBUTE_QUERY);
    };
  }

  @Bean("springSessionConversionService")
  public ConversionService springSessionConversionService(ObjectMapper objectMapper) {
    ObjectMapper copy = objectMapper.copy();
    copy.registerModules(SecurityJackson2Modules.getModules(this.classLoader));

    // Enable default typing so Jackson will include type information for polymorphic deserialization.
    // Option A (recommended): restrict to your package using BasicPolymorphicTypeValidator.
    //    BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
    //            .allowIfSubType("org.tailormap.api.security")
    //            .allowIfSubType("org.springframework.security")
    //            .build();
    //    copy.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
    // JsonTypeInfo.As.PROPERTY);

    // Option B (uncomment to use): permissive validator that allows all subtypes (insecure - only for trusted
    // input)
    copy.activateDefaultTyping(
        LaissezFaireSubTypeValidator.instance,
        ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
        JsonTypeInfo.As.PROPERTY);

    // Allow deserialization of TailormapUserDetailsImpl stored in the SecurityContext by registering a mixin
    // and a NamedType for the concrete implementation.
    //    copy.addMixIn(org.tailormap.api.security.TailormapUserDetailsImpl.class,
    //            org.tailormap.api.security.TailormapUserDetailsMixin.class);
    //    copy.registerSubtypes(new NamedType(org.tailormap.api.security.TailormapUserDetailsImpl.class,
    //            "org.tailormap.api.security.TailormapUserDetailsImpl"));

    GenericConversionService converter = new GenericConversionService();
    converter.addConverter(Object.class, byte[].class, new SerializingConverter(new JsonSerializer(copy)));
    converter.addConverter(byte[].class, Object.class, new DeserializingConverter(new JsonDeserializer(copy)));

    return converter;
  }

  @Override
  public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  static class JsonSerializer implements Serializer<Object> {
    private final ObjectMapper objectMapper;

    JsonSerializer(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    @Override
    public void serialize(@NonNull Object object, @NonNull OutputStream outputStream) throws IOException {
      this.objectMapper.writeValue(outputStream, object);
    }
  }

  static class JsonDeserializer implements Deserializer<Object> {
    private final ObjectMapper objectMapper;

    JsonDeserializer(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    @Override
    public Object deserialize(@NonNull InputStream inputStream) throws IOException {
      return this.objectMapper.readValue(inputStream, Object.class);
    }
  }
}

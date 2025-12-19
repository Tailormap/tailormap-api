/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * Ignore deserialization exceptions, see <a
 * href="https://github.com/spring-projects/spring-session/issues/529#issuecomment-2761671945">here</a>.
 */
@Configuration(proxyBeanMethods = false)
public class JdbcSessionConfiguration implements BeanClassLoaderAware {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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

    BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("org.tailormap.api.security.")
        .allowIfSubType("org.springframework.security.")
        .allowIfBaseType("java.util.")
        .build();

    ObjectMapper copy = objectMapper
        .copy()
        .configure(
            StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature(),
            (logger.isDebugEnabled() || logger.isTraceEnabled()))
        .configure(SerializationFeature.INDENT_OUTPUT, (logger.isDebugEnabled() || logger.isTraceEnabled()))
        .registerModules(SecurityJackson2Modules.getModules(this.classLoader))
        .activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

    copy.registerSubtypes(new NamedType(
        org.tailormap.api.security.TailormapUserDetailsImpl.class,
        "org.tailormap.api.security.TailormapUserDetailsImpl"));
    copy.registerSubtypes(new NamedType(
        org.tailormap.api.security.TailormapOidcUser.class, "org.tailormap.api.security.TailormapOidcUser"));

    GenericConversionService converter = new GenericConversionService();
    // Object -> byte[] (serialize to JSON bytes)
    converter.addConverter(Object.class, byte[].class, source -> {
      try {
        logger.debug("Serializing Spring Session: {}", source);
        return copy.writerFor(Object.class).writeValueAsBytes(source);
      } catch (IOException e) {
        throw new RuntimeException("Unable to serialize Spring Session.", e);
      }
    });
    // byte[] -> Object (deserialize from JSON bytes)
    converter.addConverter(byte[].class, Object.class, source -> {
      try {
        logger.debug(
            "Deserializing Spring Session from bytes, length: {} ({})",
            source.length,
            new String(source, StandardCharsets.UTF_8));
        return copy.readValue(source, Object.class);
      } catch (IOException e) {
        throw new RuntimeException("Unable to deserialize Spring Session.", e);
      }
    });

    return converter;
  }

  @Override
  public void setBeanClassLoader(@NonNull ClassLoader classLoader) {
    this.classLoader = classLoader;
  }
}

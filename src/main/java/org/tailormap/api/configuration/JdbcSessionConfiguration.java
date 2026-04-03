/*
 * Copyright (C) 2025 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package org.tailormap.api.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration(proxyBeanMethods = false)
public class JdbcSessionConfiguration implements BeanClassLoaderAware {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String CREATE_SESSION_ATTRIBUTE_QUERY = """
INSERT INTO %TABLE_NAME%_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
VALUES (?, ?, convert_from(?, 'UTF8')::jsonb)
""";

  private static final String UPDATE_SESSION_ATTRIBUTE_QUERY = """
UPDATE %TABLE_NAME%_ATTRIBUTES
SET ATTRIBUTE_BYTES = convert_from(?, 'UTF8')::jsonb
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
  public ConversionService springSessionConversionService() {

    BasicPolymorphicTypeValidator.Builder builder = BasicPolymorphicTypeValidator.builder()
        .allowIfSubType("org.tailormap.api.security.")
        .allowIfSubType("org.springframework.security.")
        .allowIfSubType("java.util.")
        .allowIfSubType(java.lang.Number.class)
        .allowIfSubType("java.time.")
        .allowIfBaseType(Object.class);

    JsonMapper mapper = JsonMapper.builder()
        .configure(
            StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION,
            (logger.isDebugEnabled() || logger.isTraceEnabled()))
        .configure(SerializationFeature.INDENT_OUTPUT, (logger.isDebugEnabled() || logger.isTraceEnabled()))

        // register mixins early so Jackson picks up the @JsonCreator constructor for TailormapUserDetails
        // implementations
        .addMixIn(
            org.tailormap.api.security.TailormapUserDetailsImpl.class,
            org.tailormap.api.security.TailormapUserDetailsImplMixin.class)
        .addMixIn(
            org.tailormap.api.security.TailormapOidcUser.class,
            org.tailormap.api.security.TailormapOidcUserMixin.class)
        .addModules(SecurityJacksonModules.getModules(this.classLoader, builder))
        .activateDefaultTyping(builder.build(), DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
        .build();

    final GenericConversionService converter = new GenericConversionService();
    // Object -> byte[] (serialize to JSON bytes)
    converter.addConverter(Object.class, byte[].class, source -> {
      try {
        logger.debug("Serializing Spring Session: {}", source);
        return mapper.writerFor(Object.class).writeValueAsBytes(source);
      } catch (JacksonException e) {
        logger.error("Error serializing Spring Session object: {}", source, e);
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
        return mapper.readValue(source, Object.class);
      } catch (JacksonException e) {
        String preview;
        try {
          String content = new String(source, StandardCharsets.UTF_8);
          int maxLength = 256;
          preview = content.length() > maxLength ? content.substring(0, maxLength) + "..." : content;
        } catch (Exception ex) {
          preview = "<unavailable>";
        }
        logger.error(
            "Error deserializing Spring Session from bytes, length: {}, preview: {}",
            source.length,
            preview,
            e);
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

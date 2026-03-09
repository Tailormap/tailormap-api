/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import java.lang.reflect.Type;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.AbstractJsonFormatMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.json.JsonMapper;

/**
 * Custom JSON format mapper for Hibernate that uses the Jackson 3 library for JSON serialization and deserialization.
 * This format mapper allows Hibernate to handle JSON types using Jackson, which is the primary JSON processing library
 * used in the application. By implementing this custom format mapper, we ensure that Hibernate can correctly serialize
 * and deserialize JSON properties in a way that is compatible with the application's JSON processing needs, especially
 * since Hibernate 7.2.x does not natively support Jackson 3. See {@link HibernateConfiguration} for how this format
 * mapper is registered with Hibernate and:
 *
 * <ul>
 *   <li><a href="https://github.com/spring-projects/spring-boot/issues/47789">Spring Boot issue #47789</a>
 *   <li><a href="https://github.com/tGrefsrud/demo-spring-boot-4-serialization-issue/pull/1">Demo project illustrating
 *       the issue with JSON serialization in Spring Boot 4 and Hibernate 7.2.x</a>
 *   <li><a href="https://hibernate.atlassian.net/browse/HHH-19890">Hibernate issue HHH-19890 regarding JSON format
 *       mappers and Jackson 3 support</a>
 *   <li><a href="https://github.com/hibernate/hibernate-orm/pull/11357">Pull request in Hibernate ORM to add support
 *       for Jackson 3 in JSON format mappers</a>
 * </ul>
 *
 * for more context on the need for a custom JSON format mapper with Hibernate 7.2.x and Jackson 3. This class should be
 * removed after Hibernate 7.3.x or higher is released/implemented in TM.
 */
public class Jackson3JsonFormatMapper extends AbstractJsonFormatMapper {

  public static final String SHORT_NAME = "jackson3";

  private final JsonMapper jsonMapper;

  public Jackson3JsonFormatMapper() {
    this(JsonMapper.shared());
  }

  public Jackson3JsonFormatMapper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public <T> void writeToTarget(T value, JavaType<T> javaType, Object target, WrapperOptions options)
      throws JacksonException {
    this.jsonMapper
        .writerFor(this.jsonMapper.constructType(javaType.getJavaType()))
        .writeValue((JsonGenerator) target, value);
  }

  @Override
  public <T> T readFromSource(JavaType<T> javaType, Object source, WrapperOptions options) throws JacksonException {
    return jsonMapper.readValue((JsonParser) source, jsonMapper.constructType(javaType.getJavaType()));
  }

  @Override
  public boolean supportsSourceType(Class<?> sourceType) {
    return JsonParser.class.isAssignableFrom(sourceType);
  }

  @Override
  public boolean supportsTargetType(Class<?> targetType) {
    return JsonGenerator.class.isAssignableFrom(targetType);
  }

  @Override
  @SuppressWarnings("TypeParameterUnusedInFormals")
  public <T> T fromString(CharSequence charSequence, Type type) {
    try {
      return jsonMapper.readValue(charSequence.toString(), jsonMapper.constructType(type));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Could not deserialize string to java type: " + type, e);
    }
  }

  @Override
  public <T> String toString(T value, Type type) {
    try {
      return jsonMapper.writerFor(jsonMapper.constructType(type)).writeValueAsString(value);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Could not serialize object of java type: " + type, e);
    }
  }
}

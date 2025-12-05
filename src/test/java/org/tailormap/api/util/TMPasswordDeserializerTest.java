/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.tailormap.api.security.InvalidPasswordException;

class TMPasswordDeserializerTest {
  private final String testJsonTemplate = "{\"password\":\"%s\"}";

  @Test
  void test_deserialize_null_password() {
    final String testJson = "{\"password\":null}";
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson(testJson),
        "null password should throw InvalidPasswordException");
  }

  @Test
  void test_deserialize_empty_input() {
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson("{}"),
        "empty input should throw InvalidPasswordException");
  }

  @Test
  void test_empty_password() {
    final String testJson = testJsonTemplate.formatted("");
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson(testJson),
        "empty password should throw InvalidPasswordException");
  }

  @Test
  void test_weak_password() {
    final String testJson = testJsonTemplate.formatted("flamingo");
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson(testJson),
        "empty password should throw InvalidPasswordException");
  }

  @Test
  void test_valid_password() throws IOException {
    final String testJson = testJsonTemplate.formatted("myValidSecret$@12");
    String actual = this.deserializeJson(testJson);

    assertNotNull(actual, "encrypted password should not be null");
    assertTrue(actual.startsWith("{bcrypt}$2a$"), "bcrypted password should start with {bcrypt}$2a$");
    assertEquals(68, actual.length(), "bcrypted password should be 8+60 characters");
  }

  private String deserializeJson(String json) throws IOException {

    ObjectMapper objectMapper = new ObjectMapper();
    DeserializationContext deserializationContext = objectMapper.getDeserializationContext();
    try (JsonParser parser = new ObjectMapper().getFactory().createParser(json)) {
      // step though the templated json
      // skip START_OBJECT
      parser.nextToken();
      // skip FIELD_NAME
      parser.nextToken();
      // use FIELD_VALUE
      parser.nextToken();

      return new TMPasswordDeserializer().deserialize(parser, deserializationContext);
    }
  }
}

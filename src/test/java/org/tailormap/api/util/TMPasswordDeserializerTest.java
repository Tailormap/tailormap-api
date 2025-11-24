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

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.tailormap.api.security.InvalidPasswordException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;

class TMPasswordDeserializerTest {
  private final String testJsonTemplate = "{\"password\":\"%s\"}";

  @Test
  void testDeserializeNullPassword() {
    final String testJson = "{\"password\":null}";
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson(testJson),
        "null password should throw InvalidPasswordException");
  }

  @Test
  void testDeserializeEmptyInput() {
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson("{}"),
        "empty input should throw InvalidPasswordException");
  }

  @Test
  void testEmptyPassword() {
    final String testJson = testJsonTemplate.formatted("");
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson(testJson),
        "empty password should throw InvalidPasswordException");
  }

  @Test
  void testWeakPassword() {
    final String testJson = testJsonTemplate.formatted("flamingo");
    assertThrows(
        InvalidPasswordException.class,
        () -> this.deserializeJson(testJson),
        "empty password should throw InvalidPasswordException");
  }

  @Test
  void testValidPassword() throws IOException {
    final String testJson = testJsonTemplate.formatted("myValidSecret$@12");
    String actual = this.deserializeJson(testJson);

    assertNotNull(actual, "encrypted password should not be null");
    assertTrue(actual.startsWith("{bcrypt}$2a$"), "bcrypted password should start with {bcrypt}$2a$");
    assertEquals(68, actual.length(), "bcrypted password should be 8+60 characters");
  }

  private String deserializeJson(String json) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    DeserializationContext deserializationContext = objectMapper._deserializationContext();
    try (JsonParser parser = new ObjectMapper().createParser(json)) {
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

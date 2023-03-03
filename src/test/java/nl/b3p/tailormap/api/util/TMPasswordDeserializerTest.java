/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import nl.b3p.tailormap.api.security.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TMPasswordDeserializerTest {
  private final String testJsonTempate = "{\"password\":\"%s\"}";
  private TMPasswordDeserializer deserializer;
  private ObjectMapper mapper;
  private DeserializationContext ctxt;

  @BeforeEach
  void setup() {
    this.mapper = new ObjectMapper();
    this.ctxt = mapper.getDeserializationContext();
    this.deserializer = new TMPasswordDeserializer(true, 8, 4);
  }

  @Test
  void testDeserializeNullPassword() {
    final String testJson = "{\"password\":null}";
    Exception thrown =
        assertThrows(
            InvalidPasswordException.class,
            () -> this.deserialiseJson(testJson),
            "null password should throw InvalidPasswordException");
    assertTrue(thrown.getMessage().contains("empty password"), "unexpected exception message");
  }

  @Test
  void testDeserializeEmptyInput() {
    Exception thrown =
        assertThrows(
            InvalidPasswordException.class,
            () -> this.deserialiseJson("{}"),
            "empty input should throw InvalidPasswordException");
    assertTrue(thrown.getMessage().contains("empty password"), "unexpected exception message");
  }

  @Test
  void testEmptyPassword() {
    final String testJson = String.format(testJsonTempate, "");
    Exception thrown =
        assertThrows(
            InvalidPasswordException.class,
            () -> this.deserialiseJson(testJson),
            "empty password should throw InvalidPasswordException");
    assertTrue(thrown.getMessage().contains("empty password"), "unexpected exception message");
  }

  @Test
  void testWeakPassword() throws IOException {
    final String testJson = String.format(testJsonTempate, "flamingo");
    Exception thrown =
        assertThrows(
            InvalidPasswordException.class,
            () -> this.deserialiseJson(testJson),
            "empty password should throw InvalidPasswordException");
    assertTrue(thrown.getMessage().contains("password strength"), "unexpected exception message");
  }

  @Test
  void testValidPassword() throws IOException {
    final String testJson = String.format(testJsonTempate, "myValidSecret$@12");
    String actual = this.deserialiseJson(testJson);

    assertNotNull(actual, "encrypted password should not be null");
    assertTrue(
        actual.startsWith("{bcrypt}$2a$"), "bcrypted password should start with {bcrypt}$2a$");
    assertEquals(68, actual.length(), "bcrypted password should be 8+60 characters");
  }

  private String deserialiseJson(String json) throws IOException {

    try (JsonParser parser = this.mapper.getFactory().createParser(json)) {
      // step though the templated json
      // skip START_OBJECT
      parser.nextToken();
      // skip FIELD_NAME
      parser.nextToken();
      // use FIELD_VALUE
      parser.nextToken();

      return deserializer.deserialize(parser, this.ctxt);
    }
  }
}

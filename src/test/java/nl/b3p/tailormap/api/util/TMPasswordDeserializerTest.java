/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
    this.deserializer = new TMPasswordDeserializer();
  }

  @Test
  void testDeserializeNullPassword() throws IOException {
    final String testJson = "{\"password\":null}";
    String actual = this.deserialiseJson(testJson);
    assertNull(actual);
  }

  @Test
  void testDeserializeEmptyInput() throws IOException {
    final String testJson = "{}";
    String actual = this.deserialiseJson(testJson);
    assertNull(actual);
  }

  @Test
  void testNonNullValidPassword() throws IOException {
    final String testJson = String.format(testJsonTempate, "myValidSecret$@12");
    String actual = this.deserialiseJson(testJson);

    assertNotNull(actual, "encrypted password should not be null");
    assertTrue(
        actual.startsWith("{bcrypt}$2a$"), "bcrypted password should start with {bcrypt}$2a$");
    assertEquals(68, actual.length(), "bcrypted password should be 8+60 characters");
  }

  private String deserialiseJson(String json) throws IOException {

    try (JsonParser parser = this.mapper.getFactory().createParser(json); ) {
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

/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.b3p.tailormap.api.security.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ActiveProfiles;

/** Test for json serializing an deserializing {@link User}. */
@JsonTest
@ActiveProfiles("test")
/** Test for json serializing an deserializing {@link User}. */
class UserTest {

  private ObjectMapper mapper;

  @Value("${tailormap-api.strong-password.validation:true}")
  private boolean enabled;

  @Value("${tailormap-api.strong-password.min-length:8}")
  private int minLength;

  @Value("${tailormap-api.strong-password.min-strength:4}")
  private int minStrength;

  @BeforeEach
  void setup() {
    InjectableValues.Std std =
        new InjectableValues.Std()
            .addValue("tailormap-api.strong-password.validation", enabled)
            .addValue("tailormap-api.strong-password.min-length", minLength)
            .addValue("tailormap-api.strong-password.min-strength", minStrength);

    this.mapper = new ObjectMapper();
    this.mapper.setInjectableValues(std);
  }

  /** Test that the password is not serialized. */
  @Test
  void testJsonSerialize() {
    final User userToSerialize =
        new User()
            .setUsername("markimarks")
            .setPassword("{bcrypt}$2a$10$hOKiZqxsvDJXMN/LbYcOeeJwWtgkKvfv834P5RsouLFpl7a8e3am2");

    String actualJson = this.mapper.valueToTree(userToSerialize).toString();
    assertTrue(
        actualJson.contains("\"username\":\"markimarks\""),
        "actualJson should contain given username");
    assertFalse(
        actualJson.contains("\"password\":"), "actualJson should not contain 'password' node");
  }

  @Test
  void testJsonDeserializeEmptyPassword() {
    final String jsonToDeserialize = "{\"username\":\"markimarks\",\"password\":\"\"}";
    Exception thrown =
        assertThrows(
            InvalidPasswordException.class,
            () -> this.mapper.readValue(jsonToDeserialize, User.class),
            "empty password should throw JsonProcessingException");
    assertTrue(thrown.getMessage().contains("empty password"), "unexpected exception message");
  }

  @Test
  void testJsonDeserializeValidPassword() throws JsonProcessingException {
    final String jsonToDeserialize =
        "{\"username\":\"markimarks\",\"password\":\"myValidSecret$@12\"}";

    User actualUser = this.mapper.readValue(jsonToDeserialize, User.class);

    assertNotNull(actualUser, "user should not be null");
    assertEquals(
        "markimarks", actualUser.getUsername(), "username should be equal to given username");

    assertTrue(
        actualUser.getPassword().startsWith("{bcrypt}$2a$"),
        "bcrypted password should start with {bcrypt}$2a$");
    assertEquals(
        68, actualUser.getPassword().length(), "bcrypted password should be 8+60 characters");
  }
}

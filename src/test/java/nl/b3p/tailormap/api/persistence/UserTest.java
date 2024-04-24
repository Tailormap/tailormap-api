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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import nl.b3p.tailormap.api.security.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ActiveProfiles;

/** Test for json serializing an deserializing {@link User}. */
@JsonTest
@ActiveProfiles("test")
class UserTest {

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();
  private final String expectedMessage =
      "Username must consist of alphanumeric characters, underscore or -";
  private ObjectMapper mapper;

  @BeforeEach
  void setup() {
    this.mapper = new ObjectMapper();
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
    assertThrows(
        InvalidPasswordException.class,
        () -> this.mapper.readValue(jsonToDeserialize, User.class),
        "empty password should throw JsonProcessingException");
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

  @Test
  void valid_Username() {
    User user = new User().setUsername("markimarks").setPassword("myValidSecret$@12");

    Set<ConstraintViolation<User>> violations = validator.validate(user);
    assertTrue(violations.isEmpty(), "violations should be empty");

    violations = validator.validate(user.setName("marki-marks"));
    assertTrue(violations.isEmpty(), "violations should be empty");

    violations = validator.validate(user.setName("marki_marks"));
    assertTrue(violations.isEmpty(), "violations should be empty");
  }

  @Test
  void blank_Username() {
    User user = new User().setUsername("").setPassword("myValidSecret$@12");

    Set<ConstraintViolation<User>> violations = validator.validate(user);
    assertEquals(1, violations.size(), "violations should not be empty");
    violations.forEach(
        action -> assertEquals(expectedMessage, action.getMessage(), "unexpected message"));
  }

  @Test
  void invalid_Username() {
    User user = new User().setUsername("app user").setPassword("myValidSecret$@12");

    Set<ConstraintViolation<User>> violations = validator.validate(user);
    assertEquals(1, violations.size(), "violations should not be empty");
    violations.forEach(
        action -> assertEquals(expectedMessage, action.getMessage(), "unexpected message"));
  }
}

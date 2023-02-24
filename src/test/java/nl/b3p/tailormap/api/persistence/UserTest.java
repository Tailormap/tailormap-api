/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** Test for json serializing an deserializing {@link User}. */
class UserTest {
  @Test
  void testJsonDeserialize() throws JsonProcessingException {
    final String jsonToDeserialize =
        "{\"username\":\"markimarks\",\"password\":\"myValidSecret$@12\"}";
    User actualUser = new ObjectMapper().readValue(jsonToDeserialize, User.class);

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
  void testJsonSerialize() {
    final User userToSerialize =
        new User()
            .setUsername("markimarks")
            .setPassword("{bcrypt}$2a$10$hOKiZqxsvDJXMN/LbYcOeeJwWtgkKvfv834P5RsouLFpl7a8e3am2");

    String actualJson = new ObjectMapper().valueToTree(userToSerialize).toString();
    assertTrue(
        actualJson.contains("\"username\":\"markimarks\""),
        "actualJson should contain given username");
    assertFalse(
        actualJson.contains("\"password\":"), "actualJson should not contain 'password' node");
  }
}

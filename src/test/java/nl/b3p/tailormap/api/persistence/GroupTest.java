/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GroupTest {
  private static Validator validator;

  private final String expectedMessage =
      "Group name must consist of alphanumeric characters, underscore or -";

  @BeforeAll
  public static void setupValidatorInstance() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void valid_GroupName() {
    Group group = new Group().setName("appGroups");

    Set<ConstraintViolation<Group>> violations = validator.validate(group);
    assertTrue(violations.isEmpty(), "violations should be empty");

    violations = validator.validate(group.setName("app-Groups"));
    assertTrue(violations.isEmpty(), "violations should be empty");

    violations = validator.validate(group.setName("app_Groups"));
    assertTrue(violations.isEmpty(), "violations should be empty");
  }

  @Test
  void blank_GroupName() {
    Group group = new Group().setName("");

    Set<ConstraintViolation<Group>> violations = validator.validate(group);
    assertEquals(1, violations.size(), "violations should not be empty");
    violations.forEach(
        action -> assertEquals(expectedMessage, action.getMessage(), "unexpected message"));
  }

  @Test
  void invalid_GroupName() {
    Group group = new Group().setName("app Groups");

    Set<ConstraintViolation<Group>> violations = validator.validate(group);
    assertEquals(1, violations.size(), "violations should be empty");
    violations.forEach(
        action -> assertEquals(expectedMessage, action.getMessage(), "unexpected message"));
  }
}

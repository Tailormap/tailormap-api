/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Configuration properties statically accessible in a Jackson deserializer. */
@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "tailormap-api.strong-password")
public class TailormapPasswordStrengthConfig {
  /**
   * {@code true} if strong password validation is enabled, {@code false} otherwise, defaults to
   * true.
   */
  private static boolean validation = true;
  /** minimum length of the password, defaults to 8. */
  private static int minLength = 8;
  /** minimum strength of the password, defaults to 4 (very strong). */
  private static int minStrength = 4;

  public void setValidation(boolean validation) {
    TailormapPasswordStrengthConfig.validation = validation;
  }

  public void setMinLength(int minLength) {
    TailormapPasswordStrengthConfig.minLength = minLength;
  }

  public void setMinStrength(int minStrength) {
    TailormapPasswordStrengthConfig.minStrength = minStrength;
  }

  public static boolean getValidation() {
    return validation;
  }

  public static int getMinLength() {
    return minLength;
  }

  public static int getMinStrength() {
    return minStrength;
  }
}

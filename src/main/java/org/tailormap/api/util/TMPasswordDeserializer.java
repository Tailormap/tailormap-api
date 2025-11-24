/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.util;

import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import me.gosimple.nbvcxz.Nbvcxz;
import me.gosimple.nbvcxz.resources.ConfigurationBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.tailormap.api.configuration.TailormapPasswordStrengthConfig;
import org.tailormap.api.security.InvalidPasswordException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class TMPasswordDeserializer extends ValueDeserializer<String> {
  private static final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

  public static PasswordEncoder encoder() {
    return encoder;
  }
  /**
   * When deserializing a JSON field containing a plaintext password validate it is strong enough and hash it with the
   * default PasswordEncoder (bcrypt).
   *
   * @param jsonParser parser
   * @param context context
   * @return The bcrypt hashed password
   * @throws JacksonException when JSON processing fails, {@code InvalidPasswordException} when the password is not
   *     strong enough
   */
  @Override
  public String deserialize(@NotNull JsonParser jsonParser, DeserializationContext context) throws JacksonException {
    ObjectReadContext objectReadContext = jsonParser.objectReadContext();
    JsonNode node = objectReadContext.readTree(jsonParser);
    if (node == null) {
      throw new InvalidPasswordException(jsonParser, "Password is required");
    }
    String password = node.asString();
    boolean validation = TailormapPasswordStrengthConfig.getValidation();
    int minLength = TailormapPasswordStrengthConfig.getMinLength();
    int minStrength = TailormapPasswordStrengthConfig.getMinStrength();
    if (validation && !validatePasswordStrength(password, minLength, minStrength)) {
      throw new InvalidPasswordException(jsonParser, "Password too short or too easily guessable");
    }
    return encoder.encode(node.asString());
  }

  public static boolean validatePasswordStrength(String password, int minLength, int minStrength) {
    if (StringUtils.isBlank(password)) {
      return false;
    }
    if (password.length() < minLength) {
      return false;
    }
    me.gosimple.nbvcxz.resources.Configuration configuration = new ConfigurationBuilder()
        .setLocale(Locale.forLanguageTag(LocaleContextHolder.getLocale().toLanguageTag()))
        .setDistanceCalc(true)
        .createConfiguration();
    return new Nbvcxz(configuration).estimate(password).getBasicScore() >= minStrength;
  }
}

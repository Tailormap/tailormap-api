/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Locale;
import me.gosimple.nbvcxz.Nbvcxz;
import me.gosimple.nbvcxz.resources.ConfigurationBuilder;
import nl.b3p.tailormap.api.configuration.TailormapPasswordStrengthConfig;
import nl.b3p.tailormap.api.security.InvalidPasswordException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public class TMPasswordDeserializer extends JsonDeserializer<String> {
  private static final PasswordEncoder encoder =
      PasswordEncoderFactories.createDelegatingPasswordEncoder();

  /**
   * When deserializing a JSON field containing a plaintext password validate it is strong enough
   * and hash it with the default PasswordEncoder (bcrypt).
   *
   * @param jsonParser parser
   * @param context context
   * @return The bcrypt hashed password
   * @throws IOException when JSON processing fails, {@code InvalidPasswordException} when the
   *     password is not strong enough
   */
  @Override
  public String deserialize(@NotNull JsonParser jsonParser, DeserializationContext context)
      throws IOException {
    ObjectCodec codec = jsonParser.getCodec();
    JsonNode node = codec.readTree(jsonParser);
    if (node == null) {
      throw new InvalidPasswordException(jsonParser, "Password is required");
    }
    String password = node.asText();
    boolean validation = TailormapPasswordStrengthConfig.getValidation();
    int minLength = TailormapPasswordStrengthConfig.getMinLength();
    int minStrength = TailormapPasswordStrengthConfig.getMinStrength();
    if (validation && !validatePasswordStrength(password, minLength, minStrength)) {
      throw new InvalidPasswordException(jsonParser, "Password too short or too easily guessable");
    }
    return encoder.encode(node.asText());
  }

  public static boolean validatePasswordStrength(String password, int minLength, int minStrength) {
    if (StringUtils.isBlank(password)) {
      return false;
    }
    if (password.length() < minLength) {
      return false;
    }
    me.gosimple.nbvcxz.resources.Configuration configuration =
        new ConfigurationBuilder()
            .setLocale(Locale.forLanguageTag(LocaleContextHolder.getLocale().toLanguageTag()))
            .setDistanceCalc(true)
            .createConfiguration();
    return new Nbvcxz(configuration).estimate(password).getBasicScore() >= minStrength;
  }
}

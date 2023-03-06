/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Locale;
import javax.validation.constraints.NotNull;
import me.gosimple.nbvcxz.Nbvcxz;
import me.gosimple.nbvcxz.resources.Configuration;
import me.gosimple.nbvcxz.resources.ConfigurationBuilder;
import me.gosimple.nbvcxz.scoring.Result;
import nl.b3p.tailormap.api.security.InvalidPasswordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public class TMPasswordDeserializer extends JsonDeserializer<String> {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * {@code true} if strong password validation is enabled, {@code false} otherwise, defaults to
   * true.
   */
  private boolean enabled = true;
  /** minimum length of the password, defaults to 8. */
  private int minLength = 8;
  /** minimum strength of the password, defaults to 4 (very strong). */
  private int minStrength = 4;

  /**
   * Encrypt the password with the default PasswordEncoder (bcrypt).
   *
   * @param jsonParser Parser used for reading JSON content
   * @param ctxt Context that can be used to access information about this deserialization activity.
   * @return the bcrypt encoded password
   * @throws IOException when JSON processing fails, {@code InvalidPasswordException} when the
   *     password is not valid
   */
  @Override
  public String deserialize(@NotNull JsonParser jsonParser, DeserializationContext ctxt)
      throws IOException {
    logger.debug("Deserializing password");
    ObjectCodec codec = jsonParser.getCodec();

    InjectableValues injectableValues = null;
    // codec can also be JsonMapper or TomlMapper as well, those extend ObjectMapper
    if (codec instanceof ObjectMapper) {
      injectableValues = ((ObjectMapper) codec).getInjectableValues();
    } else if (codec instanceof ObjectReader) {
      injectableValues = ((ObjectReader) codec).getInjectableValues();
    }

    if (null != injectableValues) {
      this.enabled =
          (Boolean)
              injectableValues.findInjectableValue(
                  "tailormap-api.strong-password.validation", ctxt, null, null);
      this.minLength =
          (Integer)
              injectableValues.findInjectableValue(
                  "tailormap-api.strong-password.min-length", ctxt, null, null);
      this.minStrength =
          (Integer)
              injectableValues.findInjectableValue(
                  "tailormap-api.strong-password.min-strength", ctxt, null, null);
    } else {
      logger.warn("No configuration values found for password deserializer, using defaults");
    }

    JsonNode node = codec.readTree(jsonParser);
    if (!validate(node, jsonParser)) {
      throw new InvalidPasswordException(jsonParser, "An invalid password was given.");
    }

    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    return encoder.encode(node.asText());
  }

  /**
   * validate password (length, complexity, etc).
   *
   * @param password the password to validate
   * @param jsonParser Parser used for reading JSON content, only used for error reporting
   * @return {@code true} if the password is valid
   * @throws InvalidPasswordException when the password is not valid according to the rules
   */
  private boolean validate(JsonNode password, JsonParser jsonParser)
      throws InvalidPasswordException {
    logger.debug("Validating password");
    boolean passwordIsValid = false;
    if (null == password || password.isNull() || password.asText().isEmpty()) {
      throw new InvalidPasswordException(jsonParser, "An empty password is not allowed.");
    }
    if (!enabled && password.asText().length() < minLength) {
      throw new InvalidPasswordException(
          jsonParser, String.format("Minimum password length of %s was not met.", minLength));
    }
    if (enabled) {
      Configuration configuration =
          new ConfigurationBuilder()
              .setLocale(Locale.forLanguageTag(LocaleContextHolder.getLocale().toLanguageTag()))
              .setDistanceCalc(true)
              .createConfiguration();

      Nbvcxz nbvcxz = new Nbvcxz(configuration);
      Result estimated = nbvcxz.estimate(password.asText());
      int strength = estimated.getBasicScore();
      logger.debug("Password strength: {}", strength);
      if (strength < minStrength) {
        throw new InvalidPasswordException(
            jsonParser, String.format("Minimum password strength of %s was not met.", minStrength));
      } else {
        passwordIsValid = true;
      }
    }
    return passwordIsValid;
  }
}

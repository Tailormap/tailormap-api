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
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

public class TMPasswordDeserializer extends JsonDeserializer<String> {
  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * Encrypt the password with the default PasswordEncoder (bcrypt).
   *
   * @param jsonParser Parser used for reading JSON content
   * @param ctxt Context that can be used to access information about this deserialization activity.
   * @return the bcrypt encoded password or {@code null} when the password is {@code null} or
   *     omitted
   */
  @Override
  public String deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
    logger.debug("Deserializing password");
    ObjectCodec codec = jsonParser.getCodec();
    JsonNode node = codec.readTree(jsonParser);
    // TODO validate password (length, complexity, etc), for now just check if it is null
    if (null == node || node.isNull()) {
      return null;
    }

    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    return encoder.encode(node.asText());
  }
}

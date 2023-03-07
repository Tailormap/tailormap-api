/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.configuration;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ObjectMapperConfiguration {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

  @Bean
  @Primary
  public ObjectMapper mapper(
      @Value("${tailormap-api.strong-password.validation:true}") boolean enabled,
      @Value("${tailormap-api.strong-password.min-length:8}") int minLength,
      @Value("${tailormap-api.strong-password.min-strength:4}") int minStrength) {

    logger.info(
        String.format(
            "Configuring strong-password policy: enabled=%s, minLength=%s, minStrength=%s",
            enabled, minLength, minStrength));
    InjectableValues.Std std = new InjectableValues.Std();
    std.addValue("tailormap-api.strong-password.validation", enabled);
    std.addValue("tailormap-api.strong-password.min-length", minLength);
    std.addValue("tailormap-api.strong-password.min-strength", minStrength);

    ObjectMapper mapper = new ObjectMapper();
    mapper.setInjectableValues(std);
    return mapper;
  }
}

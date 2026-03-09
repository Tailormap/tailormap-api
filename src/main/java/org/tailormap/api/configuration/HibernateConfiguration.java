/*
 * Copyright (C) 2026 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package org.tailormap.api.configuration;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class to customize Hibernate properties. This class registers a custom JSON format mapper for Hibernate
 * to use the Jackson 3 library for JSON serialization and deserialization. By defining a bean of type
 * {@link HibernatePropertiesCustomizer}, we can customize the Hibernate properties to specify our custom JSON format
 * mapper, which allows Hibernate to handle JSON types in a way that is compatible with the application's JSON
 * processing needs, especially since Hibernate 7.2.x does not natively support Jackson 3. See
 * {@link Jackson3JsonFormatMapper} for the implementation of the custom JSON format mapper and:
 */
@Configuration
public class HibernateConfiguration {
  @Bean
  HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
    return hibernateProperties -> {
      // Use the custom JSON format mapper for Hibernate to handle JSON types with Jackson.
      // This allows Hibernate to serialize and deserialize JSON properties using the Jackson 3 library, which is
      // used throughout the application.
      // The custom format mapper is necessary because Hibernate 7.2.x does not natively support Jackson 3,
      // and the default JSON handling may not be compatible with the application's JSON processing needs.
      hibernateProperties.put(AvailableSettings.JSON_FORMAT_MAPPER, Jackson3JsonFormatMapper.class.getName());
    };
  }
}

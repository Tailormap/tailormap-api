/*
 * Copyright (C) 2023 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */

package nl.b3p.tailormap.api.security;

import nl.b3p.tailormap.api.persistence.Group;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@Order(1)
public class ActuatorSecurityConfiguration {
  @Value("${management.endpoints.web.base-path}")
  private String basePath;

  @Bean
  public SecurityFilterChain actuatorFilterChain(
      HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {
    http.csrf()
        .csrfTokenRepository(csrfTokenRepository)
        .and()
        .securityMatcher(basePath + "/**")
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(basePath + "/health/**")
                    .permitAll()
                    .requestMatchers(basePath + "/info")
                    .permitAll()
                    .requestMatchers(basePath + "/**")
                    .hasAnyAuthority(Group.ADMIN, Group.ACTUATOR))
        .httpBasic();
    return http.build();
  }
}

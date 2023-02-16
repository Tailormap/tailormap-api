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
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Profile("spring-boot-admin")
@Configuration
public class SpringBootAdminSecurityConfiguration {

  @Value("${spring.boot.admin.context-path}")
  private String springBootAdminContextPath;

  @Bean
  public SecurityFilterChain springBootAdminFilterChain(
      HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {

    http.csrf()
        .disable() // Required for registering and for POST request to configure loggers
        .securityMatcher(springBootAdminContextPath + "/**")
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(springBootAdminContextPath + "/**")
                    .hasAnyAuthority(Group.ADMIN, Group.ACTUATOR))
        .httpBasic(); // To enable Spring Boot Admin client to register
    return http.build();
  }
}

/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import nl.b3p.tailormap.api.persistence.Group;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Value("${tailormap-api.admin.base-path}")
  private String adminApiBasePath;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    // Note: CSRF protection only required when using cookies for authentication
    // This requires an X-XSRF-TOKEN header read from the XSRF-TOKEN cookie by JavaScript so set
    // HttpOnly to false.
    // Angular has automatic XSRF protection support:
    // https://angular.io/guide/http#security-xsrf-protection
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookiePath("/");
    http.csrf()
        .csrfTokenRepository(csrfTokenRepository)
        .and()
        .authorizeHttpRequests()
        .requestMatchers(String.format("/%s/**", adminApiBasePath))
        .hasRole(Group.ADMIN)
        .anyRequest()
        .permitAll()
        .and()
        .formLogin()
        .loginProcessingUrl(String.format("/%s/login", apiBasePath))
        .and()
        .logout()
        .logoutUrl(String.format("/%s/logout", apiBasePath));
    return http.build();
  }
}

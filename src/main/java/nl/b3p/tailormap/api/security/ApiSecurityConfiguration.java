/*
 * Copyright (C) 2022 B3Partners B.V.
 *
 * SPDX-License-Identifier: MIT
 */
package nl.b3p.tailormap.api.security;

import nl.b3p.tailormap.api.persistence.Group;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ApiSecurityConfiguration implements EnvironmentAware {
  @Value("${tailormap-api.base-path}")
  private String apiBasePath;

  @Value("${tailormap-api.admin.base-path}")
  private String adminApiBasePath;

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    // Note: CSRF protection only required when using cookies for authentication. This requires an
    // X-XSRF-TOKEN header read from the XSRF-TOKEN cookie by JavaScript so set HttpOnly to false.
    // Angular has automatic XSRF protection support:
    // https://angular.io/guide/http#security-xsrf-protection
    CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokenRepository.setCookiePath("/");
    return csrfTokenRepository;
  }

  @Bean
  public SecurityFilterChain apiFilterChain(
      HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository) throws Exception {

    // Disable CSRF protection for development with HAL explorer
    // https://github.com/spring-projects/spring-data-rest/issues/1347
    if (ArrayUtils.contains(environment.getActiveProfiles(), "disable-csrf")) {
      http.csrf().disable();
    } else {
      http = http.csrf().csrfTokenRepository(csrfTokenRepository).and();
    }

    http.securityMatchers(matchers -> matchers.requestMatchers(apiBasePath + "/**"))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(adminApiBasePath + "/users")
                    .hasAnyAuthority(Group.ADMIN, Group.ADMIN_USERS)
                    .requestMatchers(adminApiBasePath + "/**")
                    .hasAuthority(Group.ADMIN))
        .formLogin()
        .loginProcessingUrl(apiBasePath + "/login")
        .and()
        .logout()
        .logoutUrl(apiBasePath + "/logout")
        .logoutSuccessHandler(
            (request, response, authentication) -> response.sendError(HttpStatus.OK.value(), "OK"));
    return http.build();
  }
}
